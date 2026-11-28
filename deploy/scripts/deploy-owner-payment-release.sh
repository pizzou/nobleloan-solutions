#!/usr/bin/env bash
# Run from the live checkout. The caller supplies the reviewed commit and branch.
set -euo pipefail
umask 077

release=${1:?Usage: deploy-owner-payment-release.sh <reviewed-commit> <release-branch>}
branch=${2:?The release branch is required}
[[ "$release" =~ ^[0-9a-f]{40}$ ]] || { echo "Supply the full reviewed commit SHA."; exit 1; }

cd /opt/nobleloan-solutions
compose=(docker compose -p nobleloan-staging
  -f docker-compose.yml -f docker-compose.staging-lowmem.yml)

stop() { printf 'Stopped: %s\n' "$*" >&2; exit 1; }

test -f docker-compose.staging-lowmem.yml || stop "the live low-memory override is missing."
git diff --quiet && git diff --cached --quiet || stop "the checkout has tracked changes."

backend=$("${compose[@]}" ps -q backend)
frontend=$("${compose[@]}" ps -q frontend)
postgres=$("${compose[@]}" ps -q postgres)
nginx=$("${compose[@]}" ps -q nginx)
for id in "$backend" "$frontend" "$postgres" "$nginx"; do
  [[ -n "$id" ]] || stop "the existing Compose stack could not be identified."
  [[ "$(docker inspect --format '{{.State.Health.Status}}' "$id")" == healthy ]] ||
    stop "an existing service is unhealthy."
done
[[ "$(docker inspect --format '{{.Id}}' "$postgres")" == "$(docker inspect --format '{{.Id}}' loansaas-postgres)" ]] ||
  stop "the selected project does not manage the live PostgreSQL container."

current=$(git rev-parse HEAD)
git fetch origin "$branch" </dev/null
[[ "$(git rev-parse FETCH_HEAD)" == "$release" ]] || stop "the branch head differs from the reviewed commit."
git merge-base --is-ancestor "$current" "$release" || stop "the server has commits outside the reviewed release."

query() {
  docker exec "$postgres" sh -c \
    'exec psql -X -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d loansaas_nobleloansolutions -c "$1"' sh "$1" </dev/null
}

owners=$(query "SELECT count(*) FROM public.app_users u JOIN public.roles r ON r.id = u.role_id
  WHERE r.name = 'BUSINESS_OWNER' AND u.status = 'ACTIVE'
    AND u.deleted_at IS NULL AND NULLIF(BTRIM(u.email), '') IS NOT NULL;")
[[ "$owners" =~ ^[0-9]+$ && "$owners" -gt 0 ]] ||
  stop "configure an active Business Owner account with an email address before enabling manual payment reviews."

version=$(query "SELECT COALESCE(MAX(version::integer), 0) FROM public.flyway_schema_history
  WHERE success AND version ~ '^[0-9]+$';")
[[ "$version" =~ ^[0-9]+$ && "$version" -ge 143 && "$version" -le 145 ]] ||
  stop "live Flyway version is outside the reviewed upgrade path."

stamp=$(date -u +%Y%m%dT%H%M%SZ)
directory=/root/nobleloan-deploy-backups
mkdir -p "$directory"
printf '%s\n' "$current" > "$directory/pre-deploy-$stamp.commit"
docker tag "$(docker inspect --format '{{.Image}}' "$backend")" "nobleloan-backend-rollback:$stamp"
docker tag "$(docker inspect --format '{{.Image}}' "$frontend")" "nobleloan-frontend-rollback:$stamp"
printf 'Saved rollback images with tag %s\n' "$stamp"

backup="$directory/pre-deploy-$stamp.dump"
docker exec "$postgres" sh -c   'exec pg_dump -Fc -U "$POSTGRES_USER" -d loansaas_nobleloansolutions' < /dev/null > "$backup"
[[ -s "$backup" ]] || stop "the database backup is empty."
docker exec -i "$postgres" pg_restore --list < "$backup" > /dev/null
sha256sum "$backup" > "$backup.sha256"
sha256sum --check "$backup.sha256"
printf 'Database backup: %s\n' "$backup"

git switch --detach "$release"
python3 deploy/scripts/validate-migrations.py
"${compose[@]}" config --format json | python3 -c '
import json, sys
env = json.load(sys.stdin)["services"]["backend"].get("environment", {})
if str(env.get("MAIL_ENABLED", "")).lower() != "true" or not all(
    str(env.get(key) or "").strip() for key in ("MAIL_FROM", "BREVO_API_KEY")
):
    sys.exit("Stopped: the target backend needs configured mail for owner notifications.")
if str(env.get("APP_HISTORICAL_REPAYMENT_ENABLED", "")).lower() == "true":
    sys.exit("Stopped: disable the historical receipt correction runner for this rollout.")
'

"${compose[@]}" build backend </dev/null
"${compose[@]}" build frontend </dev/null

wait_healthy() {
  local service=$1 timeout=$2 elapsed id state
  for ((elapsed=0; elapsed<timeout; elapsed+=5)); do
    id=$("${compose[@]}" ps -a -q "$service")
    if [[ -n "$id" ]]; then
      state=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")
      if [[ "$state" == healthy ]]; then
        printf '%s is healthy\n' "$service"
        return 0
      fi
    fi
    sleep 5
  done
  "${compose[@]}" ps -a "$service"
  stop "$service did not become healthy. Check its logs before taking another deployment action."
}

"${compose[@]}" up -d --no-deps --no-build --pull never backend </dev/null
wait_healthy backend 600
[[ "$(query "SELECT count(*) FROM public.flyway_schema_history WHERE version IN ('144','145') AND success;")" == 2 ]] ||
  stop "Flyway has not confirmed both payment-review migrations."

"${compose[@]}" up -d --no-deps --no-build --pull never frontend </dev/null
wait_healthy frontend 300

"${compose[@]}" exec -T nginx nginx -t </dev/null
"${compose[@]}" exec -T nginx nginx -s reload </dev/null
for path in / /login /healthz; do
  code=$(curl -fsS --max-time 20 --resolve nobleloansolutions.rw:443:127.0.0.1     -o /dev/null -w '%{http_code}' "https://nobleloansolutions.rw$path")
  [[ "$code" == "200" ]] || stop "$path returned HTTP $code."
  printf '%s HTTP %s\n' "$path" "$code"
done
"${compose[@]}" ps
printf 'Deployed %s\n' "$release"
