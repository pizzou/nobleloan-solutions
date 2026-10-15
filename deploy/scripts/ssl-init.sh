#!/usr/bin/env bash
# ================================================================
# Let's Encrypt SSL initialization for AOS VM deployment
# Usage: ./deploy/scripts/ssl-init.sh admin@example.com domain.tld [www.domain.tld]
# ================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

[[ -f .env ]] || { echo ".env not found." >&2; exit 1; }
set -a; # shellcheck disable=SC1091
source .env; set +a

EMAIL="${1:-${EMAIL:-}}"
shift || true
if [[ -z "$EMAIL" || "$EMAIL" != *@* ]]; then
  echo "Usage: $0 <email> <domain> [domain2 ...]" >&2
  exit 1
fi
if [[ "$#" -gt 0 ]]; then
  DOMAINS=("$@")
elif [[ -n "${DOMAIN:-}" ]]; then
  DOMAINS=("$DOMAIN" "www.$DOMAIN")
else
  echo "A production domain is required." >&2
  exit 1
fi
PRIMARY_DOMAIN="${DOMAINS[0]}"

if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose); else COMPOSE=(docker-compose); fi
command -v openssl >/dev/null 2>&1 || { echo "openssl is required." >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }

mkdir -p docker/nginx/ssl

# Bootstrap Nginx with a temporary self-signed certificate only long enough to
# serve the ACME HTTP challenge. deploy.sh never creates or accepts this as a
# production certificate.
if [[ ! -s docker/nginx/ssl/cert.pem || ! -s docker/nginx/ssl/key.pem ]]; then
  echo "[SSL] Creating temporary bootstrap certificate for ACME setup..."
  openssl req -x509 -nodes -days 1 -newkey rsa:2048 \
    -keyout docker/nginx/ssl/key.pem \
    -out docker/nginx/ssl/cert.pem \
    -subj "/C=RW/O=LoanSaaS/OU=ACME Bootstrap/CN=${PRIMARY_DOMAIN}" \
    >/dev/null 2>&1
fi

# Build/start the dependency services first because production Nginx has a
# depends_on health requirement.
"${COMPOSE[@]}" up -d postgres
DB_READY=false
for _ in $(seq 1 60); do
  if "${COMPOSE[@]}" exec -T postgres pg_isready -U "${POSTGRES_ADMIN_USERNAME:-loansaas}" -d loansaas_nobleloansolutions >/dev/null 2>&1; then
    DB_READY=true
    break
  fi
  sleep 2
done
if [[ "$DB_READY" != true ]]; then
  "${COMPOSE[@]}" logs --tail=100 postgres || true
  echo "[SSL] PostgreSQL did not become ready; refusing to continue." >&2
  exit 1
fi

"${COMPOSE[@]}" up -d backend frontend

for svc in backend frontend; do
  SERVICE_HEALTHY=false
  for _ in $(seq 1 180); do
    cid="$("${COMPOSE[@]}" ps -q "$svc" 2>/dev/null || true)"
    if [[ -n "$cid" ]]; then
      status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)"
      if [[ "$status" == "healthy" ]]; then
        SERVICE_HEALTHY=true
        break
      fi
      if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
        "${COMPOSE[@]}" logs --tail=100 "$svc" || true
        echo "[SSL] $svc failed before becoming healthy (status=$status)." >&2
        exit 1
      fi
    fi
    sleep 2
  done
  if [[ "$SERVICE_HEALTHY" != true ]]; then
    "${COMPOSE[@]}" logs --tail=100 "$svc" || true
    echo "[SSL] $svc did not become healthy within the startup window." >&2
    exit 1
  fi
done

"${COMPOSE[@]}" up -d nginx

DOMAIN_ARGS=()
for d in "${DOMAINS[@]}"; do DOMAIN_ARGS+=("-d" "$d"); done

echo "[SSL] Requesting Let's Encrypt certificate for: ${DOMAINS[*]}"
"${COMPOSE[@]}" --profile ssl run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot \
  --email "$EMAIL" \
  --agree-tos --no-eff-email --non-interactive \
  "${DOMAIN_ARGS[@]}"

# certbot and Nginx now share the host ssl directory. Copy the issued files out of
# the Let's Encrypt store using a shell entrypoint so no host-path assumptions exist
# inside the container.
"${COMPOSE[@]}" --profile ssl run --rm --entrypoint /bin/sh certbot -c \
  "cp /etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem /etc/nginx/ssl/cert.pem && \
   cp /etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem /etc/nginx/ssl/key.pem && \
   chmod 600 /etc/nginx/ssl/key.pem"

"${COMPOSE[@]}" exec -T nginx nginx -t
"${COMPOSE[@]}" exec -T nginx nginx -s reload

HTTP_CODE="$(curl -ksS --resolve "${PRIMARY_DOMAIN}:443:127.0.0.1" -o /dev/null -w '%{http_code}' "https://${PRIMARY_DOMAIN}/healthz" || true)"
[[ "$HTTP_CODE" == "200" ]] || { echo "[SSL] HTTPS validation failed: HTTP $HTTP_CODE" >&2; exit 1; }

echo "[SSL] PASS — real Let's Encrypt certificate installed for: ${DOMAINS[*]}"
echo "[SSL] Install the weekly renewal cron entry with: ./deploy/scripts/install-production-cron.sh"
