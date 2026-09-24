#!/usr/bin/env bash
set -euo pipefail

HOST="${1:-http://localhost:8080}"
ADMIN=00000000-0000-0000-0000-00000000a001
SPECIALIST=00000000-0000-0000-0000-00000000b001
SPECIALIST_PROFILE=00000000-0000-0000-0000-00000000b101
CLIENT=00000000-0000-0000-0000-00000000c001
EXPORT="$(cd "$(dirname "$0")" && pwd)/demo/telegram-masha.json"
BODY=$(mktemp); trap 'rm -f "$BODY"' EXIT

call() {
  local expected=$1 method=$2 path=$3 user=$4 data=${5:-}
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" "$HOST$path")
  [[ $user != - ]] && args+=(-H "X-User-Id: $user")
  [[ -n $data ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  local status; status=$(curl "${args[@]}")
  printf '  %-6s %-62s → %s\n' "$method" "$path" "$status"
  if [[ $status != "$expected" ]]; then echo "  ожидался $expected: $(cat "$BODY")"; exit 1; fi
}
field() { python3 -c "import json,sys; j=json.load(open('$BODY')); print($1)"; }

echo "1. Персона и выгрузка"
call 201 POST /api/v1/personas "$CLIENT" '{"name":"Маша","relationshipKind":"EX_PARTNER","tagCodes":["sarcastic"]}'
PERSONA=$(field 'j["id"]')
status=$(curl -s -o "$BODY" -w '%{http_code}' -H "X-User-Id: $CLIENT" -F "file=@$EXPORT;type=application/json" "$HOST/api/v1/personas/$PERSONA/imports")
printf '  %-6s %-62s → %s\n' POST "/api/v1/personas/{id}/imports (multipart)" "$status"; [[ $status == 202 ]] || { cat "$BODY"; exit 1; }
IMPORT=$(field 'j["id"]')
call 200 GET "/api/v1/imports/$IMPORT" "$CLIENT";           echo "     импорт: $(field 'j["status"], j["messageCount"], "сообщений, из них их:", j["theirMessageCount"]')"
call 200 GET "/api/v1/personas/$PERSONA" "$CLIENT";         echo "     персона: $(field 'j["status"], [t["code"] for t in j["tags"]]')"
call 200 GET "/api/v1/personas/$PERSONA/profile" "$CLIENT"; echo "     профиль v$(field 'j["versionNo"]')"

echo "2. Диалог"
call 201 POST /api/v1/conversations "$CLIENT" "{\"personaId\":\"$PERSONA\"}"
CONV=$(field 'j["id"]')
for text in "привет, спишь?" "где ты была весь вечер?" "помнишь фонтан?"; do
  call 201 POST "/api/v1/conversations/$CONV/messages" "$CLIENT" "{\"text\":\"$text\"}"
  echo "     я: $text"; echo "     Маша: $(field 'j["reply"]["text"]')"
  REPLY=${REPLY:-$(field 'j["reply"]["id"]')}
done
call 503 POST "/api/v1/conversations/$CONV/messages" "$CLIENT" '{"text":"ты тут? [[llm:down]]"}'; echo "     $(field 'j["code"]'): сообщение пользователя сохранено"
call 201 POST "/api/v1/conversations/$CONV/messages" "$CLIENT" '{"text":"я не хочу жить"}';         echo "     guardrails: $(field 'j["reply"]["text"]')"
call 200 GET "/api/v1/conversations/$CONV/messages?limit=3" "$CLIENT"
CURSOR=$(field 'j["nextCursor"]'); echo "     порция: $(field 'len(j["items"])') сообщения, nextCursor=${CURSOR:0:16}…"
call 200 GET "/api/v1/conversations/$CONV/messages?limit=3&cursor=$CURSOR" "$CLIENT"

echo "3. Специалист и консультация"
call 200 GET "/api/v1/specialists?specialization=breakup" "$CLIENT"
call 200 GET "/api/v1/specialists/$SPECIALIST_PROFILE/slots" "$CLIENT"
SLOT=$(field 'j[0]["id"]')
call 201 POST /api/v1/consultations "$CLIENT" "{\"slotId\":\"$SLOT\",\"sharedConversationId\":\"$CONV\"}"
CONSULTATION=$(field 'j["id"]')
call 409 POST /api/v1/consultations "$ADMIN" "{\"slotId\":\"$SLOT\"}";              echo "     $(field 'j["code"]')"
call 200 PATCH "/api/v1/consultations/$CONSULTATION" "$SPECIALIST" '{"status":"CONFIRMED"}'
call 200 GET "/api/v1/conversations/$CONV/messages" "$SPECIALIST";                   echo "     специалист видит расшаренную беседу: $(field 'len(j["items"])') сообщений"

echo "4. Модерация"
call 201 POST /api/v1/moderation/flags "$CLIENT" "{\"messageId\":\"$REPLY\",\"reason\":\"ABUSE\",\"comment\":\"грубит\"}"
FLAG=$(field 'j["id"]')
call 200 GET "/api/v1/conversations/$CONV/messages" "$ADMIN";                        echo "     администратор видит только флагнутые: $(field 'len(j["items"])') сообщения"
call 200 GET "/api/v1/moderation/flags?status=OPEN" "$ADMIN";                        echo "     очередь: $(field '[(f["source"], f["reason"]) for f in j]')"
call 200 PATCH "/api/v1/moderation/flags/$FLAG" "$ADMIN" '{"status":"RESOLVED","resolution":"персона архивирована","archivePersona":true}'
call 200 GET "/api/v1/personas/$PERSONA" "$CLIENT";                                  echo "     персона: $(field 'j["status"]')"
call 409 POST "/api/v1/conversations/$CONV/messages" "$CLIENT" '{"text":"ты тут?"}'; echo "     $(field 'j["code"]')"

echo "5. Уведомления и метрики"
call 200 GET /api/v1/notifications "$CLIENT";                                        echo "     $(field '[n["type"] for n in j]')"
call 200 GET /api/v1/admin/metrics "$ADMIN";                                          echo "     $(field '{k: v for k, v in j["metrics"].items() if k.startswith(("personas", "agent.runs", "flags"))}')"

echo "6. Ошибки"
call 401 GET /api/v1/personas -
call 400 GET "/api/v1/personas?size=51" "$CLIENT";                                  echo "     $(field 'j["errors"]')"
call 404 GET "/api/v1/personas/$PERSONA" "$ADMIN"
echo "Сценарий пройден."
