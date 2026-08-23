#!/usr/bin/env bash
# Production cutover for ivelox-core (Spring) + note FE redeploy.
# Prereq: fly auth login
set -euo pipefail

APP=ivelox-core
FE_URL="${FRONTEND_URL:-https://ivelox-app.fly.dev}"
API_URL="${API_URL:-https://ivelox-core.fly.dev}"
REGION=sin

echo "==> Auth check"
fly auth whoami

echo "==> Ensure Postgres (Fly managed)"
if ! fly postgres list 2>/dev/null | grep -q ivelox-db; then
  fly postgres create --name ivelox-db --region "$REGION" --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 1
fi
fly postgres attach ivelox-db -a "$APP" || true

echo "==> Set app secrets (reads local .env for telegram/gemini if present)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
set -a
# map old Go TELEGRAM_TOKEN -> TELEGRAM_BOT_TOKEN
if [[ -f "$ROOT/.env" ]]; then
  # carefully source only needed keys without printing
  TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
  TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:-}"
  GEMINI_API_KEY="${GEMINI_API_KEY:-}"
  # parse .env
  while IFS='=' read -r k v; do
    case "$k" in
      TELEGRAM_BOT_TOKEN) TELEGRAM_BOT_TOKEN="$v" ;;
      TELEGRAM_TOKEN) TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-$v}" ;;
      TELEGRAM_CHAT_ID) TELEGRAM_CHAT_ID="$v" ;;
      GEMINI_API_KEY) GEMINI_API_KEY="$v" ;;
    esac
  done < <(grep -E '^(TELEGRAM_BOT_TOKEN|TELEGRAM_TOKEN|TELEGRAM_CHAT_ID|GEMINI_API_KEY)=' "$ROOT/.env" | sed 's/\r$//')
fi
set +a

JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"

fly secrets set -a "$APP" \
  JWT_SECRET="$JWT_SECRET" \
  TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:?set TELEGRAM_BOT_TOKEN or TELEGRAM_TOKEN in .env}" \
  TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:?set TELEGRAM_CHAT_ID in .env}" \
  FRONTEND_URL="$FE_URL" \
  HEALTH_ENABLED=true \
  GEMINI_API_KEY="${GEMINI_API_KEY:-}" \
  GEMINI_MODEL=gemini-2.0-flash

echo "==> Deploy Spring from current branch"
fly deploy -a "$APP" --remote-only

echo "==> Smoke"
curl -fsS "$API_URL/api/v1/health"
echo
curl -fsS "$API_URL/api/v1/features"
echo
echo "Done. Redeploy FE with VITE_API_URL=$API_URL (push main or fly deploy in ivelox-app)."
