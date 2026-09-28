#!/usr/bin/env bash
# Renew Let's Encrypt certificates and refresh the host-mounted Nginx copies.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
[[ -f .env ]] || { echo ".env not found." >&2; exit 1; }
set -a; # shellcheck disable=SC1091
source .env; set +a
PRIMARY_DOMAIN="${DOMAIN:?DOMAIN is required}"
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose); else COMPOSE=(docker-compose); fi

"${COMPOSE[@]}" --profile ssl run --rm --entrypoint /bin/sh certbot -c \
  "certbot renew --non-interactive --deploy-hook 'cp /etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem /etc/nginx/ssl/cert.pem && cp /etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem /etc/nginx/ssl/key.pem && chmod 600 /etc/nginx/ssl/key.pem'"

"${COMPOSE[@]}" exec -T nginx nginx -t
"${COMPOSE[@]}" exec -T nginx nginx -s reload
echo "[SSL RENEW] PASS — certificate renewal check completed."
