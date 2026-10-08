#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
HOST=${AIEX_BASE_URL:-http://localhost:${AIEX_PORT:-8080}}
CONFIG=${CONFIG_BASE_URL:-http://localhost:${CONFIG_SERVER_PORT:-8888}}
EUREKA=${EUREKA_BASE_URL:-http://localhost:${EUREKA_PORT:-8761}}
CLIENT=00000000-0000-0000-0000-00000000c001
BODY=$(mktemp)
ACCOUNT_STOPPED=false

cleanup() {
  if $ACCOUNT_STOPPED; then
    docker compose -f "$ROOT/docker-compose.yml" start account-service >/dev/null
  fi
  rm -f "$BODY"
}
trap cleanup EXIT

wait_for() {
  local url=$1 user=${2:-}
  local args=(--fail --silent --max-time 5)
  [[ -n $user ]] && args+=(-H "X-User-Id: $user")
  for attempt in $(seq 1 120); do
    if curl "${args[@]}" "$url" >"$BODY"; then return; fi
    sleep 2
  done
  echo "Недоступен $url" >&2
  exit 1
}

echo "Config Server"
wait_for "$CONFIG/account-service/microservice"
python3 - "$BODY" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
assert data["name"] == "account-service"
sources = data["propertySources"]
assert any("account-service.yml" in source["name"] for source in sources), sources
assert any("application.yml" in source["name"] for source in sources), sources
assert any(source["source"].get("server.port") == 8081 for source in sources), sources
PY

echo "Eureka"
registered=false
for attempt in $(seq 1 120); do
  if curl --fail --silent --max-time 3 -H 'Accept: application/json' "$EUREKA/eureka/apps" >"$BODY" &&
    python3 - "$BODY" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))["applications"].get("application", [])
if isinstance(data, dict):
    data = [data]
up = set()
for app in data:
    instances = app.get("instance", [])
    if isinstance(instances, dict):
        instances = [instances]
    if any(instance["status"] == "UP" for instance in instances):
        up.add(app["name"])
required = {"ACCOUNT-SERVICE", "PERSONA-SERVICE", "DIALOG-SERVICE", "CARE-SERVICE", "NOTIFICATION-SERVICE", "GATEWAY"}
sys.exit(0 if required <= up else 1)
PY
  then
    registered=true
    break
  fi
  sleep 2
done
$registered || { echo "Сервисы не зарегистрировались в Eureka" >&2; exit 1; }

echo "Gateway"
wait_for "$HOST/actuator/health"
curl --fail --silent "$HOST/" >"$BODY"
wait_for "$HOST/api/v1/personas" "$CLIENT"
wait_for "$HOST/api/v1/notifications" "$CLIENT"
curl --fail --silent --location --max-time 10 "$HOST/swagger-ui.html" >"$BODY"
for service in account persona dialog care; do
  wait_for "$HOST/v3/api-docs/$service"
  python3 - "$BODY" <<'PY'
import json, sys
paths = json.load(open(sys.argv[1]))["paths"]
assert paths and all(path.startswith("/api/") for path in paths), paths
PY
done
bash "$ROOT/docs/demo.sh" "$HOST"

if [[ ${1:-} == --outage ]]; then
  echo "Circuit Breaker: остановка account-service"
  docker compose -f "$ROOT/docker-compose.yml" stop account-service >/dev/null
  ACCOUNT_STOPPED=true
  for attempt in $(seq 1 8); do
    status=$(curl --silent --max-time 15 -o "$BODY" -w '%{http_code}' \
      -H "X-User-Id: $CLIENT" "$HOST/api/v1/personas")
    [[ $status == 503 ]] || { cat "$BODY"; echo "Ожидался HTTP 503, получен $status" >&2; exit 1; }
    python3 - "$BODY" <<'PY'
import json, sys
assert json.load(open(sys.argv[1]))["code"] == "SERVICE_UNAVAILABLE"
PY
  done
  docker compose -f "$ROOT/docker-compose.yml" exec -T persona-service \
    wget -qO- http://localhost:8082/actuator/circuitbreakers >"$BODY"
  python3 - "$BODY" <<'PY'
import json, sys
circuits = json.load(open(sys.argv[1]))["circuitBreakers"]
assert circuits["account-service"]["state"] == "OPEN", circuits
PY
  docker compose -f "$ROOT/docker-compose.yml" start account-service >/dev/null
  ACCOUNT_STOPPED=false
  recovered=false
  for attempt in $(seq 1 90); do
    if curl --fail --silent --max-time 10 -H "X-User-Id: $CLIENT" "$HOST/api/v1/personas" >"$BODY"; then
      recovered=true
      break
    fi
    sleep 2
  done
  $recovered || { echo "account-service не восстановился" >&2; exit 1; }
fi

echo "Лабораторная №2: проверки пройдены."
