import argparse
import os
import secrets
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
admin_email = os.environ.get('ADMIN_EMAIL') or os.environ.get('BOOTSTRAP_ADMIN_EMAIL')
admin_password = os.environ.get('ADMIN_PASSWORD') or os.environ.get('BOOTSTRAP_ADMIN_PASSWORD')
if not admin_email or not admin_password:
    parser.error('Set ADMIN_EMAIL and ADMIN_PASSWORD (or BOOTSTRAP_ADMIN_EMAIL/PASSWORD)')
tokens = {}


def request(base, path, method='GET', body=None, user=None):
    headers = {'Accept': 'application/json'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if user:
        headers['Authorization'] = 'Bearer ' + tokens.get(user, 'invalid')
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
    assert spec['components']['securitySchemes']['bearerAuth']['scheme'] == 'bearer', name
print('PASS: shared Swagger UI configuration and all three public OpenAPI specifications')

status, session = request(args.gateway, '/api/v1/auth/login', 'POST', {'email': admin_email, 'password': admin_password})
assert status == 200, status
admin_id = session['user']['id']
tokens[admin_id] = session['accessToken']

for path in ('/api/v1/users', '/api/v1/personas', '/api/v1/notifications'):
    eventually(lambda path=path: request(args.gateway, path, user=admin_id),
               lambda result: result[0] == 200)

new_password = secrets.token_urlsafe(24)
status, user = request(args.gateway, '/api/v1/users', 'POST', {
    'email': f'smoke-{uuid.uuid4()}@example.com', 'displayName': 'Lab3 smoke', 'password': new_password
}, admin_id)
assert status == 201, (status, user)
assert 'password' not in user and 'passwordHash' not in user
user_id = user['id']
status, session = request(args.gateway, '/api/v1/auth/login', 'POST', {'email': user['email'], 'password': new_password})
assert status == 200, status
tokens[user_id] = session['accessToken']
new_user = {'email': f'forbidden-{uuid.uuid4()}@example.com', 'displayName': 'X', 'password': new_password, 'roles': ['ADMIN']}
assert request(args.gateway, '/api/v1/users', 'POST', new_user)[0] == 401
assert request(args.gateway, '/api/v1/users', 'POST', new_user, user_id)[0] == 403
assert request(args.gateway, '/api/v1/admin/metrics', user=user_id)[0] == 403
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
assert request(args.gateway, f'/internal/users/{user_id}', user=admin_id)[0] == 403
status, metrics = request(args.gateway, '/api/v1/admin/metrics',
                          user=admin_id)
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
