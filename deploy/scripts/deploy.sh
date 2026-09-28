set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

BOLD='\033[1m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
RED='\033[0;31m'; NC='\033[0m'

log()  { echo -e "${GREEN}[DEPLOY]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

command -v docker >/dev/null 2>&1 || err "Docker Engine is required."
if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
else
    command -v docker-compose >/dev/null 2>&1 || err "Docker Compose v2 is required."
    COMPOSE=(docker-compose)
fi
command -v curl >/dev/null 2>&1 || err "curl is required."

[[ -f "$ROOT_DIR/.env" ]] || err ".env not found. Copy .env.example to .env and configure real production values."
chmod 600 "$ROOT_DIR/.env" 2>/dev/null || true
set -a
# shellcheck disable=SC1091
source "$ROOT_DIR/.env"
set +a

[[ "${APP_ENVIRONMENT:-}" == "production" ]] || err "APP_ENVIRONMENT must be production."
[[ "${SPRING_PROFILE:-default}" != "dev" ]] || err "SPRING_PROFILE=dev is not allowed for production deployment."
[[ "${DB_PASSWORD:-}" != "" ]] || err "DB_PASSWORD is required."
[[ "${JWT_SECRET:-}" != "" ]] || err "JWT_SECRET is required."
[[ "${CORS_ORIGINS:-}" != *"*"* ]] || err "CORS_ORIGINS may not contain '*'."
[[ "${WEBSOCKET_ALLOWED_ORIGINS:-}" != "" ]] || err "WEBSOCKET_ALLOWED_ORIGINS is required."
[[ "${FRONTEND_URL:-}" != "" ]] || err "FRONTEND_URL is required."
[[ "${MAIL_ENABLED:-true}" == "true" ]] || err "MAIL_ENABLED must be true for production OTP delivery."
[[ "${BREVO_API_KEY:-}" != "" ]] || err "BREVO_API_KEY is required."
[[ "${SMS_ENABLED:-true}" == "true" ]] || err "SMS_ENABLED must be true for production OTP delivery."

if grep -Eq 'nobleloan-solutions\.(vercel|onrender)\.com|provider\.example\.com|yourdomain\.com|REPLACE_WITH|change_me' "$ROOT_DIR/.env"; then
    err ".env still contains known development/placeholder values. Replace them before deployment."
fi

DOMAIN="${DOMAIN:-nobleloansolutions.rw}"

if [[ ! -s "$ROOT_DIR/docker/nginx/ssl/cert.pem" || ! -s "$ROOT_DIR/docker/nginx/ssl/key.pem" ]]; then
    err "Production TLS certificate/key are missing. Run ./deploy/scripts/ssl-init.sh first, or install the real AOS/domain certificate into docker/nginx/ssl/cert.pem and key.pem."
fi

log "Validating Docker Compose configuration..."
"${COMPOSE[@]}" config >/dev/null

log "Pulling required base images..."
"${COMPOSE[@]}" pull postgres nginx

log "Building application images with production tests..."
"${COMPOSE[@]}" build --pull backend frontend

log "Starting PostgreSQL..."
"${COMPOSE[@]}" up -d postgres
for i in $(seq 1 60); do
    if "${COMPOSE[@]}" exec -T postgres pg_isready -U loansaas -d loansaas_nobleloansolutions >/dev/null 2>&1; then
        break
    fi
    [[ "$i" -eq 60 ]] && err "PostgreSQL did not become ready."
    sleep 2
done

log "Starting backend (Flyway migrations run automatically)..."
"${COMPOSE[@]}" up -d backend

wait_for_health() {
    local service="$1"
    local seconds="$2"
    local cid status
    for _ in $(seq 1 "$seconds"); do
        cid="$("${COMPOSE[@]}" ps -q "$service" 2>/dev/null || true)"
        if [[ -n "$cid" ]]; then
            status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)"
            if [[ "$status" == "healthy" ]]; then
                log "$service is healthy."
                return 0
            fi
            if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
                "${COMPOSE[@]}" logs --tail=120 "$service" || true
                err "$service failed health check (status=$status)."
            fi
        fi
        sleep 1
    done
    "${COMPOSE[@]}" logs --tail=120 "$service" || true
    err "$service did not become healthy within ${seconds}s."
}

wait_for_health backend 300

log "Starting frontend..."
"${COMPOSE[@]}" up -d frontend
wait_for_health frontend 180

log "Starting Nginx..."
"${COMPOSE[@]}" up -d nginx
wait_for_health nginx 120

log "Validating public HTTPS and readiness through Nginx..."
for _ in $(seq 1 30); do
    code="$(curl -ksS --resolve "${DOMAIN}:443:127.0.0.1" -o /dev/null -w '%{http_code}' "https://${DOMAIN}/healthz" || true)"
    if [[ "$code" == "200" ]]; then
        break
    fi
    sleep 2
done
code="$(curl -ksS --resolve "${DOMAIN}:443:127.0.0.1" -o /dev/null -w '%{http_code}' "https://${DOMAIN}/healthz" || true)"
[[ "$code" == "200" ]] || err "Nginx public /healthz returned HTTP $code instead of 200."

log "Deployment completed successfully."
echo "======================================================="
echo "  LOANSAAS PRO — AOS PRODUCTION DEPLOYED"
echo "======================================================="
echo "  App:      https://${DOMAIN}/"
echo "  API:      https://${DOMAIN}/api"
echo "  Health:   https://${DOMAIN}/healthz"
echo "  Logs:     ${COMPOSE[*]} logs -f backend"
echo "======================================================="
