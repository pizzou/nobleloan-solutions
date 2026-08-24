#!/usr/bin/env bash
# Controlled PostgreSQL production restore. Requires explicit confirmation.
set -euo pipefail
umask 077
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
BACKUP_FILE="${1:?Usage: restore.sh <backup.sql.gz[.enc]>}"
DB_NAME="${DB_NAME:-loansaas_nobleloansolutions}"
[[ -f "$BACKUP_FILE" ]] || { echo "Backup file not found: $BACKUP_FILE" >&2; exit 1; }

./deploy/scripts/verify-backup.sh "$BACKUP_FILE"

echo "WARNING: this will overwrite the production database '$DB_NAME'."
read -r -p "Type RESTORE-$DB_NAME to continue: " confirm
[[ "$confirm" == "RESTORE-$DB_NAME" ]] || { echo "Cancelled."; exit 0; }

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
INPUT_FILE="$BACKUP_FILE"
if [[ "$BACKUP_FILE" == *.enc ]]; then
  : "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required}"
  openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPTION_KEY \
    -in "$BACKUP_FILE" -out "$WORKDIR/backup.sql.gz"
  INPUT_FILE="$WORKDIR/backup.sql.gz"
fi

echo "[RESTORE] Stopping backend..."
docker-compose stop backend

echo "[RESTORE] Restoring verified backup into $DB_NAME..."
gunzip -c "$INPUT_FILE" | docker-compose exec -T postgres psql \
  --username=loansaas --dbname="$DB_NAME" --set ON_ERROR_STOP=1 >/tmp/loansaas-restore.log

echo "[RESTORE] Starting backend..."
docker-compose start backend

echo "[RESTORE] Waiting for readiness..."
for i in $(seq 1 60); do
  if curl -fsS http://localhost:8080/actuator/health/readiness | grep -q '"status":"UP"'; then
    echo "[RESTORE] PASS — application readiness is UP."
    exit 0
  fi
  sleep 3
done

echo "[RESTORE] FAIL — backend did not become ready after restore." >&2
exit 1
