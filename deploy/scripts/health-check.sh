#!/usr/bin/env bash
# ================================================================
# LoanSaaS Pro — AOS production health check
# ================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
FAILED=0
ok()   { echo -e "${GREEN}[OK]${NC}   $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAILED=1; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

command -v docker >/dev/null 2>&1 || { echo "Docker is required." >&2; exit 1; }
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose); else COMPOSE=(docker-compose); fi
[[ -f .env ]] || { echo ".env not found." >&2; exit 1; }
set -a; # shellcheck disable=SC1091
source .env; set +a
DOMAIN="${DOMAIN:-nobleloansolutions.rw}"
DB_NAME="${DB_NAME:-loansaas_nobleloansolutions}"

echo "================================================"
echo "  LoanSaaS Pro — AOS System Health Check"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "================================================"

for svc in postgres backend frontend nginx; do
  cid="$("${COMPOSE[@]}" ps -q "$svc" 2>/dev/null || true)"
  if [[ -z "$cid" ]]; then fail "$svc: not running"; continue; fi
  status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo unknown)"
  case "$status" in
    healthy) ok "$svc: healthy" ;;
    starting) warn "$svc: starting" ;;
    *) fail "$svc: $status" ;;
  esac
done

echo ""
echo "--- Database ---"
if "${COMPOSE[@]}" exec -T postgres pg_isready -U loansaas -d "$DB_NAME" >/dev/null 2>&1; then
  ok "PostgreSQL: accepting connections"
  SIZE="$(${COMPOSE[@]} exec -T postgres psql -U loansaas -d "$DB_NAME" -tAc "SELECT pg_size_pretty(pg_database_size('$DB_NAME'));" 2>/dev/null | tr -d '[:space:]' || true)"
  [[ -n "$SIZE" ]] && ok "Database size: $SIZE"
else
  fail "PostgreSQL: cannot connect"
fi

echo ""
echo "--- Backend readiness ---"
BACKEND_STATUS="$(${COMPOSE[@]} exec -T backend wget -qO- http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null || true)"
if grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$BACKEND_STATUS"; then
  ok "Spring Boot readiness: UP"
else
  fail "Spring Boot readiness: DOWN"
fi

echo ""
echo "--- Frontend ---"
if "${COMPOSE[@]}" exec -T frontend wget -qO- http://127.0.0.1:3000/ >/dev/null 2>&1; then
  ok "Next.js: responding"
else
  fail "Next.js: not responding"
fi

echo ""
echo "--- Nginx / TLS ---"
HTTP_CODE="$(curl -ksS --resolve "${DOMAIN}:443:127.0.0.1" -o /dev/null -w '%{http_code}' "https://${DOMAIN}/healthz" 2>/dev/null || true)"
[[ "$HTTP_CODE" == "200" ]] && ok "Nginx HTTPS /healthz: HTTP 200" || fail "Nginx HTTPS /healthz: HTTP ${HTTP_CODE:-unknown}"

DISK="$(df -h / | awk 'END{print $5}')"
MEM="$(free -h | awk '/^Mem:/{print $3 "/" $2}')"
ok "Disk usage: ${DISK:-unknown}"
ok "Memory: ${MEM:-unknown}"

echo ""
if [[ "$FAILED" -eq 0 ]]; then echo -e "${GREEN}ALL CHECKS PASSED${NC}"; else echo -e "${RED}SOME CHECKS FAILED${NC}"; fi
exit "$FAILED"
