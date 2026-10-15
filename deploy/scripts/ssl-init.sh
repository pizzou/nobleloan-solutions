#!/usr/bin/env bash
# Initial Let's Encrypt certificate bootstrap.
# Does not start the production application or bypass approval gates.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

[[ -f .env ]] || {
    echo ".env not found. Configure production values first." >&2
    exit 1
}

set -a
# shellcheck disable=SC1091
source .env
set +a

EMAIL="${1:-${EMAIL:-}}"
if [[ "$#" -gt 0 ]]; then
    shift
fi

if [[ ! "$EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
    echo "Usage: $0 <email> <domain> [domain2 ...]" >&2
    exit 1
fi

if [[ "$#" -gt 0 ]]; then
    DOMAINS=("$@")
elif [[ -n "${DOMAIN:-}" ]]; then
    # Request only the primary domain by default.
    # Add www explicitly only after its DNS record is configured.
    DOMAINS=("$DOMAIN")
else
    echo "A production domain is required." >&2
    exit 1
fi

PRIMARY_DOMAIN="${DOMAINS[0]}"

# Validate hostnames before using them in paths and commands.
for domain in "${DOMAINS[@]}"; do
    [[ "$domain" =~ ^[A-Za-z0-9.-]+$ &&
       "$domain" != .* &&
       "$domain" != *. &&
       "$domain" != *..* ]] || {
        echo "Invalid domain name: $domain" >&2
        exit 1
    }

    IFS='.' read -r -a labels <<< "$domain"

    for label in "${labels[@]}"; do
        [[ "$label" =~ ^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$ ]] || {
            echo "Invalid DNS label in domain: $domain" >&2
            exit 1
        }
    done
done

command -v docker >/dev/null 2>&1 || {
    echo "Docker Engine is required." >&2
    exit 1
}

if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
else
    echo "Docker Compose is required." >&2
    exit 1
fi

command -v openssl >/dev/null 2>&1 || {
    echo "openssl is required." >&2
    exit 1
}

command -v curl >/dev/null 2>&1 || {
    echo "curl is required." >&2
    exit 1
}

command -v sha256sum >/dev/null 2>&1 || {
    echo "sha256sum is required." >&2
    exit 1
}

compose_base() {
    "${COMPOSE[@]}" \
        -f "$ROOT_DIR/docker-compose.yml" "$@"
}

compose_acme() {
    "${COMPOSE[@]}" \
        -f "$ROOT_DIR/docker-compose.yml" \
        -f "$ROOT_DIR/deploy/docker-compose.acme-bootstrap.yml" \
        "$@"
}

mkdir -p docker/nginx/ssl
chmod 700 docker/nginx/ssl

# If a healthy production Nginx exists, reuse it. Otherwise, use
# a temporary HTTP-only Nginx without starting backend or frontend.
BOOTSTRAP_STARTED=false
COMPOSE_MODE="bootstrap"

NGINX_ID="$(compose_base ps -q nginx 2>/dev/null || true)"

if [[ -n "$NGINX_ID" ]]; then
    NGINX_STATE="$(
        docker inspect --format='{{.State.Status}}' \
            "$NGINX_ID" 2>/dev/null || true
    )"

    NGINX_HEALTH="$(
        docker inspect \
            --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
            "$NGINX_ID" 2>/dev/null || true
    )"

    if [[ "$NGINX_STATE" == "running" &&
          "$NGINX_HEALTH" == "healthy" ]]; then
        COMPOSE_MODE="base"
        echo "[SSL] Reusing healthy production Nginx."
    elif [[ "$NGINX_STATE" == "running" ]]; then
        echo "[SSL] Existing Nginx is running but not healthy." >&2
        echo "[SSL] Inspect it before continuing; it will not be stopped automatically." >&2
        exit 1
    fi
fi

cleanup() {
    rc=$?
    trap - EXIT

    if [[ "$BOOTSTRAP_STARTED" == true ]]; then
        compose_acme stop acme-bootstrap >/dev/null 2>&1 || true

        if [[ "$rc" -eq 0 ]]; then
            compose_acme rm -f acme-bootstrap >/dev/null 2>&1 || true
        fi
    fi

    exit "$rc"
}
trap cleanup EXIT

if [[ "$COMPOSE_MODE" == "bootstrap" ]]; then
    echo "[SSL] Starting temporary HTTP-only ACME service."
    echo "[SSL] Application services will not be started by this script."

    compose_acme up -d --no-deps acme-bootstrap
    BOOTSTRAP_STARTED=true
fi

DOMAIN_ARGS=()
for domain in "${DOMAINS[@]}"; do
    DOMAIN_ARGS+=("-d" "$domain")
done

# Request the certificate using the shared Let's Encrypt webroot.
if [[ "$COMPOSE_MODE" == "bootstrap" ]]; then
    compose_acme --profile ssl run --rm --no-deps \
        --entrypoint certbot certbot certonly \
        --webroot -w /var/www/certbot \
        --email "$EMAIL" \
        --agree-tos --no-eff-email --non-interactive \
        "${DOMAIN_ARGS[@]}"

    compose_acme --profile ssl run --rm --no-deps \
        --entrypoint /bin/sh certbot -ec \
        "test -s '/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem' &&
         test -s '/etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem' &&
         cp '/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem' /etc/nginx/ssl/cert.pem &&
         cp '/etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem' /etc/nginx/ssl/key.pem &&
         chmod 600 /etc/nginx/ssl/key.pem"
else
    compose_base --profile ssl run --rm --no-deps \
        --entrypoint certbot certbot certonly \
        --webroot -w /var/www/certbot \
        --email "$EMAIL" \
        --agree-tos --no-eff-email --non-interactive \
        "${DOMAIN_ARGS[@]}"

    compose_base --profile ssl run --rm --no-deps \
        --entrypoint /bin/sh certbot -ec \
        "test -s '/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem' &&
         test -s '/etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem' &&
         cp '/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem' /etc/nginx/ssl/cert.pem &&
         cp '/etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem' /etc/nginx/ssl/key.pem &&
         chmod 600 /etc/nginx/ssl/key.pem"
fi

chmod 644 docker/nginx/ssl/cert.pem
chmod 600 docker/nginx/ssl/key.pem

openssl x509 -in docker/nginx/ssl/cert.pem -noout -checkend 0 >/dev/null || {
    echo "[SSL] Certificate is expired or not yet valid." >&2
    exit 1
}

for domain in "${DOMAINS[@]}"; do
    openssl x509 -in docker/nginx/ssl/cert.pem \
        -noout -checkhost "$domain" >/dev/null || {
        echo "[SSL] Certificate does not cover $domain." >&2
        exit 1
    }
done

CERT_PUB_HASH="$(
    openssl x509 -in docker/nginx/ssl/cert.pem -pubkey -noout |
        openssl pkey -pubin -outform DER 2>/dev/null |
        sha256sum | awk '{print $1}'
)"

KEY_PUB_HASH="$(
    openssl pkey -in docker/nginx/ssl/key.pem -pubout -outform DER 2>/dev/null |
        sha256sum | awk '{print $1}'
)"

[[ -n "$CERT_PUB_HASH" &&
   "$CERT_PUB_HASH" == "$KEY_PUB_HASH" ]] || {
    echo "[SSL] Certificate and private key do not match." >&2
    exit 1
}

if [[ "$COMPOSE_MODE" == "base" ]]; then
    compose_base exec -T nginx nginx -t
    compose_base exec -T nginx nginx -s reload

    HTTP_CODE="$(
        curl -ksS \
            --resolve "${PRIMARY_DOMAIN}:443:127.0.0.1" \
            -o /dev/null -w '%{http_code}' \
            "https://${PRIMARY_DOMAIN}/healthz" || true
    )"

    [[ "$HTTP_CODE" == "200" ]] || {
        echo "[SSL] HTTPS readiness returned HTTP $HTTP_CODE, expected 200." >&2
        exit 1
    }
fi

if [[ "$BOOTSTRAP_STARTED" == true ]]; then
    compose_acme stop acme-bootstrap >/dev/null
    compose_acme rm -f acme-bootstrap >/dev/null
    BOOTSTRAP_STARTED=false

    echo "[SSL] Certificate prepared."
    echo "[SSL] The production application has NOT been started."
    echo "[SSL] Run deploy.sh only after production values and evidence gates are approved."
else
    echo "[SSL] Certificate installed and healthy production Nginx reloaded."
fi