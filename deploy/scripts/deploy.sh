#!/bin/bash
# ================================================================
# LoanSaaS Pro — Production Deployment Script
# Usage: ./deploy/scripts/deploy.sh
# ================================================================
set -euo pipefail

BOLD='\033[1m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
RED='\033[0;31m'; NC='\033[0m'

log()  { echo -e "${GREEN}[DEPLOY]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# Check required tools
command -v docker &>/dev/null || err "docker not found. Please install Docker Engine first."
if docker compose version &>/dev/null; then
    COMPOSE=(docker compose)
elif command -v docker-compose &>/dev/null; then
    COMPOSE=(docker-compose)
else
    err "Docker Compose is not installed. Install the Docker Compose plugin first."
fi

command -v curl &>/dev/null || err "curl not found. Please install it first."
command -v openssl &>/dev/null || err "openssl not found. Please install it first."

# Check .env exists
[ -f ".env" ] || err ".env not found. Copy .env.example to .env and fill in values."

log "Loading environment..."
set -a; source .env; set +a

# Validate required vars
[ -z "${DB_PASSWORD:-}" ]  && err "DB_PASSWORD not set in .env"
[ -z "${JWT_SECRET:-}" ]   && err "JWT_SECRET not set in .env"

# Check SSL certs exist (or generate self-signed for testing)
if [ ! -f "docker/nginx/ssl/cert.pem" ]; then
    warn "SSL certificates not found. Generating self-signed cert for testing..."
    mkdir -p docker/nginx/ssl
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout docker/nginx/ssl/key.pem \
        -out  docker/nginx/ssl/cert.pem \
        -subj "/C=RW/ST=Kigali/L=Kigali/O=LoanSaaS/CN=localhost" 2>/dev/null
    log "Self-signed cert generated. Use ssl-init.sh for production Let's Encrypt cert."
fi

log "Pulling latest images..."
"${COMPOSE[@]}" pull postgres nginx 2>/dev/null || true

log "Building application images..."
"${COMPOSE[@]}" build --no-cache

log "Starting database..."
"${COMPOSE[@]}" up -d postgres
log "Waiting for PostgreSQL to be ready..."
until "${COMPOSE[@]}" exec -T postgres pg_isready -U loansaas -d loansaas_nobleloansolutions 2>/dev/null; do
    echo -n "."; sleep 2
done
echo ""
log "PostgreSQL is ready!"

log "Starting backend (Flyway migrations will run automatically)..."
"${COMPOSE[@]}" up -d backend
log "Waiting for backend health check..."
max_wait=300; waited=0
while true; do
    status=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' loansaas-backend 2>/dev/null || true)
    if [ "$status" = "healthy" ]; then
        break
    fi
    if [ "$status" = "unhealthy" ] || [ "$status" = "exited" ]; then
        "${COMPOSE[@]}" logs --tail=100 backend || true
        err "Backend failed health check (status: ${status})"
    fi
    echo -n "."; sleep 3; waited=$((waited+3))
    [ $waited -ge $max_wait ] && "${COMPOSE[@]}" logs --tail=100 backend && err "Backend failed to become healthy in ${max_wait}s"
done
echo ""
log "Backend is healthy!"

log "Starting frontend..."
"${COMPOSE[@]}" up -d frontend

log "Starting Nginx..."
"${COMPOSE[@]}" up -d nginx

log ""
log "======================================================="
log "  LOANSAAS PRO DEPLOYED SUCCESSFULLY!"
log "======================================================="
log "  App:     https://localhost  (or your domain)"
log "  API:     https://localhost/api"
log "  Swagger: https://localhost/swagger-ui.html"
log "  Logs:    ${COMPOSE[@]} logs -f backend"
log "======================================================="
