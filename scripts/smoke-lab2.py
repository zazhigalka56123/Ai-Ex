import argparse
import re
from pathlib import Path
import json
import time
import urllib.error
import urllib.request
import uuid
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--gateway', default='http://localhost:8080')
parser.add_argument('--eureka', default='http://localhost:8761')
parser.add_argument('--config', default='http://localhost:8888')
parser.add_argument('--resilience', action='store_true')
args = parser.parse_args()


def request(base, path, method='GET', body=None, user=None):
    headers = {'Accept': 'application/json'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if user:
        headers['X-User-Id'] = user
    req = urllib.request.Request(base + path, method=method, headers=headers,
                                 data=None if body is None else json.dumps(body).encode())
    try:
        response = urllib.request.urlopen(req, timeout=100)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        result = json.loads(raw) if raw else None
        if base == args.gateway and path.startswith('/api/'):
            trace_ids = response.headers.get_all('X-Trace-Id') or []
            assert len(trace_ids) == 1, (path, trace_ids)
            assert re.fullmatch(r'[A-Za-z0-9-]{8,64}', trace_ids[0]), trace_ids
            if isinstance(result, dict) and 'traceId' in result:
                assert result['traceId'] == trace_ids[0], result
        return response.status, result


def eventually(action, predicate, timeout=120):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = action()
            if predicate(last):
                return last
        except (OSError, ValueError) as error:
            last = str(error)
        time.sleep(2)
    raise AssertionError(f'Timed out: {last}')


expected = {'CORE-SERVICE', 'IAM-SERVICE', 'NOTIFICATION-SERVICE', 'GATEWAY', 'CONFIG-SERVER'}
eventually(lambda: request(args.eureka, '/eureka/apps'),
           lambda result: result[0] == 200 and expected <= {
               app['name'] for app in result[1]['applications']['application']
           })
for name in ('core-service', 'iam-service', 'notification-service', 'gateway', 'discovery-server'):
    status, config = request(args.config, f'/{name}/default')
    assert status == 200 and config['propertySources'], name
    sources = config['propertySources']
    assert any(f'/{name}/application.yaml' in source['name'] for source in sources), name
    properties = {}
    for source in reversed(sources):
        properties.update(source['source'])
    assert properties['resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls'] == 5, name


def static_resource(path):
    with urllib.request.urlopen(args.gateway + path, timeout=100) as response:
        return response.read()


for path, expected_content in (('/', b'<html'), ('/app.js', b''), ('/app.css', b'')):
    eventually(lambda path=path: static_resource(path),
               lambda content: bool(content) and expected_content in content)

status, swagger = eventually(lambda: request(args.gateway, '/v3/api-docs/swagger-config'),
                             lambda result: result[0] == 200)
urls = {entry['name']: entry['url'] for entry in swagger['urls']}
for name, url, endpoint in (
    ('core', '/v3/api-docs/all', '/api/v1/personas'),
    ('iam', '/v3/api-docs/iam', '/api/v1/users'),
    ('notifications', '/v3/api-docs/notifications', '/api/v1/notifications'),
):
    assert urls.get(name) == url, urls
    status, spec = eventually(lambda url=url: request(args.gateway, url), lambda result: result[0] == 200)
    assert endpoint in spec['paths'], (name, spec['paths'])
    assert all(path.startswith('/api/') for path in spec['paths']), name
    assert spec['servers'] == [{'url': '/'}], (name, spec['servers'])
    if name == 'notifications':
        properties = spec['components']['schemas']['NotificationResponse']['properties']
        assert set(properties) == {'id', 'type', 'payload', 'createdAt'}, properties
        parameters = spec['paths'][endpoint]['get'].get('parameters', [])
        assert all(parameter['name'] != 'status' for parameter in parameters), parameters
print('PASS: shared Swagger UI configuration and all three public OpenAPI specifications')

for path in ('/api/v1/users', '/api/v1/personas', '/api/v1/notifications'):
    eventually(lambda path=path: request(args.gateway, path, user='00000000-0000-0000-0000-00000000a001'),
               lambda result: result[0] == 200)

status, user = request(args.gateway, '/api/v1/users', 'POST', {
    'email': f'smoke-{uuid.uuid4()}@example.com', 'displayName': 'Lab2 smoke'
})
assert status == 201, (status, user)
user_id = user['id']
status, persona = request(args.gateway, '/api/v1/personas', 'POST', {
    'name': 'Smoke persona', 'relationshipKind': 'FRIEND'
}, user_id)
assert status == 201, (status, persona)
persona_id = persona['id']
status, body = request(args.gateway, f'/api/v1/personas/{persona_id}', 'DELETE', user=user_id)
assert status == 204, (status, body)
status, notifications = eventually(lambda: request(args.gateway, '/api/v1/notifications', user=user_id),
           lambda result: result[0] == 200 and any(
               item['type'] == 'PERSONA_ARCHIVED' and item['payload']['personaId'] == persona_id
               for item in result[1]))
assert all(set(item) == {'id', 'type', 'payload', 'createdAt'} for item in notifications), notifications
assert request(args.gateway, '/api/v1/notifications')[0] == 401
assert request(args.gateway, '/api/v1/notifications', user=str(uuid.uuid4()))[0] == 401
assert request(args.gateway, f'/internal/users/{user_id}')[0] == 404
status, metrics = request(args.gateway, '/api/v1/admin/metrics',
                          user='00000000-0000-0000-0000-00000000a001')
assert status == 200 and 'notifications.sent' in metrics['metrics'] and 'users.active' in metrics['metrics'], (status, metrics)
print('PASS: Config Server, Eureka, Gateway, Feign, IAM, core, R2DBC notifications, outbox and metrics')

if args.resilience:
    compose = ['docker-compose'] if shutil.which('docker-compose') else ['docker', 'compose']
    project_dir = Path(__file__).resolve().parents[1]

    def service_status(path):
        result = subprocess.run(
            compose + ['exec', '-T', 'gateway', 'wget', '-S', '-O', '/dev/null', '-T', '20',
                       '--header', f'X-User-Id: {user_id}', f'http://notification-service:8082{path}'],
            cwd=project_dir, capture_output=True, text=True, timeout=30,
        )
        statuses = re.findall(r'HTTP/\S+\s+(\d{3})', result.stdout + result.stderr)
        assert statuses, result.stdout + result.stderr
        return int(statuses[-1])

    def breaker_state_value(state):
        result = subprocess.run(
            compose + ['exec', '-T', 'gateway', 'wget', '-qO-', '-T', '10',
                       f'http://notification-service:8082/actuator/metrics/resilience4j.circuitbreaker.state?tag=state:{state}'],
            cwd=project_dir, capture_output=True, text=True, check=True, timeout=20,
        )
        return sum(item['value'] for item in json.loads(result.stdout)['measurements'])

    subprocess.run(compose + ['stop', 'iam-service'], cwd=project_dir, check=True)
    try:
        assert service_status('/actuator/health/readiness') == 200
        for attempt in range(6):
            status = service_status('/api/v1/notifications')
            print(f'Notifications -> stopped IAM: attempt {attempt + 1}, HTTP {status}')
            assert status == 503, status
        assert breaker_state_value('open') >= 1
        status, problem = request(args.gateway, '/api/v1/notifications', user=user_id)
        assert status == 503 and problem['code'] == 'SERVICE_UNAVAILABLE', (status, problem)
        assert problem['traceId'], problem
        print(f'Gateway response: HTTP {status}, {json.dumps(problem, ensure_ascii=False)}')
        print('PASS: Notifications remains UP, returns 503 itself, and its Feign Circuit Breaker is OPEN')
    finally:
        subprocess.run(compose + ['start', 'iam-service'], cwd=project_dir, check=True)
    eventually(
        lambda: (service_status('/api/v1/notifications'), service_status('/api/v1/notifications')),
        lambda statuses: statuses == (200, 200), timeout=180,
    )
    assert breaker_state_value('closed') >= 1
    print('PASS: IAM restored, Notifications returns 200, Circuit Breaker is CLOSED')
    eventually(
        lambda: (request(args.gateway, '/api/v1/personas', user=user_id)[0],
                 request(args.gateway, '/api/v1/personas', user=user_id)[0]),
        lambda statuses: statuses == (200, 200), timeout=180,
    )
    print('PASS: core also resolves restored IAM and returns 200')

    subprocess.run(compose + ['stop', 'notification-service'], cwd=project_dir, check=True)
    try:
        for attempt in range(6):
            status, problem = request(args.gateway, '/api/v1/notifications', user=user_id)
            assert status == 503 and problem['code'] == 'SERVICE_UNAVAILABLE', (status, problem)
            print(f'Gateway -> stopped Notifications: attempt {attempt + 1}, HTTP {status}')
        status, queued = request(args.gateway, '/api/v1/personas', 'POST', {
            'name': 'Queued notification', 'relationshipKind': 'FRIEND'
        }, user_id)
        assert status == 201, (status, queued)
        assert request(args.gateway, f"/api/v1/personas/{queued['id']}", 'DELETE', user=user_id)[0] == 204
    finally:
        subprocess.run(compose + ['start', 'notification-service'], cwd=project_dir, check=True)
    eventually(lambda: request(args.gateway, '/api/v1/notifications', user=user_id),
               lambda result: result[0] == 200 and any(
                   item['payload'].get('personaId') == queued['id'] for item in result[1]), timeout=180)
    print('PASS: Circuit Breaker returns 503 and outbox delivers after service recovery')
