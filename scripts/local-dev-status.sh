#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8081}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:9527}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
CURL_TIMEOUT="${CURL_TIMEOUT:-8}"
CURL_OPTS=(-fsS --connect-timeout 2 --max-time "$CURL_TIMEOUT")

print_section() {
  printf '\n== %s ==\n' "$1"
}

check_port() {
  local name="$1"
  local port="$2"
  local line

  line="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk 'NR==2 {print $1 " pid=" $2}')"
  if [[ -n "$line" ]]; then
    printf 'UP   %-18s :%s  %s\n' "$name" "$port" "$line"
  else
    printf 'DOWN %-18s :%s\n' "$name" "$port"
  fi
}

json_get() {
  python3 -c '
import json
import sys

path = sys.argv[1].split(".")
payload = json.load(sys.stdin)
value = payload
for key in path:
    value = value[key]
print(value)
' "$1"
}

print_section "Project"
printf 'root     %s\n' "$ROOT_DIR"
printf 'frontend %s\n' "$FRONTEND_URL"
printf 'backend  %s\n' "$BACKEND_URL"

print_section "Ports"
check_port "frontend vite" 9527
check_port "spring backend" 8081
check_port "redis" 6379
check_port "minio api" 19000
check_port "minio console" 19001
check_port "kafka" 9092
check_port "local es" 9200
check_port "embedding" 8001
check_port "chat model" 8000

print_section "Backend"
login_payload="$(curl "${CURL_OPTS[@]}" -X POST "$BACKEND_URL/api/v1/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
token="$(printf '%s' "$login_payload" | json_get 'data.token')"
printf 'login    OK (%s)\n' "$ADMIN_USERNAME"

monitoring_payload="$(curl "${CURL_OPTS[@]}" -H "Authorization: Bearer $token" "$BACKEND_URL/api/v1/admin/monitoring/status")"
preflight_payload="$(curl "${CURL_OPTS[@]}" -H "Authorization: Bearer $token" "$BACKEND_URL/api/v1/upload/preflight")"

printf 'preflight %s\n' "$(printf '%s' "$preflight_payload" | json_get 'data.message')"
printf 'redis     %s\n' "$(printf '%s' "$monitoring_payload" | json_get 'data.components.redis.status')"
printf 'minio     %s\n' "$(printf '%s' "$monitoring_payload" | json_get 'data.components.minio.status')"
printf 'es        %s count=%s\n' \
  "$(printf '%s' "$monitoring_payload" | json_get 'data.components.elasticsearch.status')" \
  "$(printf '%s' "$monitoring_payload" | json_get 'data.components.elasticsearch.knowledgeBaseCount')"
printf 'chat      %s\n' "$(printf '%s' "$monitoring_payload" | json_get 'data.components.vllmChat.status')"
printf 'embedding %s\n' "$(printf '%s' "$monitoring_payload" | json_get 'data.components.vllmEmbedding.status')"
printf 'kafka     %s lag=%s\n' \
  "$(printf '%s' "$monitoring_payload" | json_get 'data.components.kafka.status')" \
  "$(printf '%s' "$monitoring_payload" | json_get 'data.components.kafka.totalLag')"

print_section "Tip"
printf 'Open %s and sign in with %s / %s\n' "$FRONTEND_URL" "$ADMIN_USERNAME" "$ADMIN_PASSWORD"
