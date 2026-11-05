import argparse
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
        with urllib.request.urlopen(req, timeout=65) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


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
eventually(lambda: request(args.gateway, '/api/v1/notifications', user=user_id),
           lambda result: result[0] == 200 and any(
               item['type'] == 'PERSONA_ARCHIVED' and item['payload']['personaId'] == persona_id
               for item in result[1]))
assert request(args.gateway, '/api/v1/notifications')[0] == 401
assert request(args.gateway, '/api/v1/notifications', user=str(uuid.uuid4()))[0] == 401
assert request(args.gateway, f'/internal/users/{user_id}')[0] == 404
status, metrics = request(args.gateway, '/api/v1/admin/metrics',
                          user='00000000-0000-0000-0000-00000000a001')
assert status == 200 and 'notifications.sent' in metrics['metrics'] and 'users.active' in metrics['metrics'], (status, metrics)
print('PASS: Config Server, Eureka, Gateway, Feign, IAM, core, R2DBC notifications, outbox and metrics')

if args.resilience:
    compose = ['docker-compose'] if shutil.which('docker-compose') else ['docker', 'compose']
    subprocess.run(compose + ['stop', 'notification-service'], check=True)
    try:
        for _ in range(6):
            assert request(args.gateway, '/api/v1/notifications', user=user_id)[0] == 503
        status, queued = request(args.gateway, '/api/v1/personas', 'POST', {
            'name': 'Queued notification', 'relationshipKind': 'FRIEND'
        }, user_id)
        assert status == 201, (status, queued)
        assert request(args.gateway, f"/api/v1/personas/{queued['id']}", 'DELETE', user=user_id)[0] == 204
    finally:
        subprocess.run(compose + ['start', 'notification-service'], check=True)
    eventually(lambda: request(args.gateway, '/api/v1/notifications', user=user_id),
               lambda result: result[0] == 200 and any(
                   item['payload'].get('personaId') == queued['id'] for item in result[1]), timeout=180)
    print('PASS: Circuit Breaker returns 503 and outbox delivers after service recovery')
