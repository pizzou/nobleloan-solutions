#!/usr/bin/env bash
# Restore a production backup into an isolated database inside the private
# PostgreSQL container. This intentionally does not require host port 5432.
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

[[ -f .env ]] || { echo ".env is required." >&2; exit 1; }
set -a
# shellcheck disable=SC1091
source .env
set +a

BACKUP_FILE="${1:?Usage: $0 <backup.sql.gz[.enc]>}"
[[ -f "$BACKUP_FILE" ]] || { echo "[DR] Backup file not found: $BACKUP_FILE" >&2; exit 1; }

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "[DR] Docker Compose v2 is required." >&2
  exit 1
fi
command -v openssl >/dev/null 2>&1 || { echo "[DR] openssl is required." >&2; exit 1; }
command -v gzip >/dev/null 2>&1 || { echo "[DR] gzip is required." >&2; exit 1; }

./deploy/scripts/verify-backup.sh "$BACKUP_FILE"

DB_NAME="${DB_NAME:-loansaas_nobleloansolutions}"
DR_TEST_DB="loansaas_restore_test_$(date -u +%Y%m%d_%H%M%S)_$$"
WORKDIR="$(mktemp -d)"

cleanup() {
  rm -rf "$WORKDIR"
  "${COMPOSE[@]}" exec -T postgres psql -U loansaas -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS \"$DR_TEST_DB\";" >/dev/null 2>&1 || true
}
trap cleanup EXIT

INPUT="$BACKUP_FILE"
if [[ "$BACKUP_FILE" == *.enc ]]; then
  : "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required for encrypted backups}"
  openssl enc -d -aes-256-cbc -pbkdf2 \
    -pass env:BACKUP_ENCRYPTION_KEY \
    -in "$BACKUP_FILE" \
    -out "$WORKDIR/backup.sql.gz"
  INPUT="$WORKDIR/backup.sql.gz"
fi

gzip -t "$INPUT"

"${COMPOSE[@]}" exec -T postgres psql -U loansaas -d "$DB_NAME" \
  -v ON_ERROR_STOP=1 \
  -c "CREATE DATABASE \"$DR_TEST_DB\";" >/dev/null

echo "[DR] Restoring backup into isolated database inside PostgreSQL container: $DR_TEST_DB"
gunzip -c "$INPUT" | "${COMPOSE[@]}" exec -T postgres psql \
  -U loansaas \
  -d "$DR_TEST_DB" \
  -v ON_ERROR_STOP=1 \
  >/dev/null

echo "[DR] Running structural and accounting checks..."
"${COMPOSE[@]}" exec -T postgres psql -U loansaas -d "$DR_TEST_DB" -v ON_ERROR_STOP=1 <<'SQL'
DO $$
BEGIN
  IF to_regclass('public.loans') IS NULL THEN
    RAISE EXCEPTION 'DR restore failed: loans table missing';
  END IF;
  IF to_regclass('public.payments') IS NULL THEN
    RAISE EXCEPTION 'DR restore failed: payments table missing';
  END IF;
  IF to_regclass('public.journal_entries') IS NULL THEN
    RAISE EXCEPTION 'DR restore failed: journal_entries table missing';
  END IF;
  IF to_regclass('public.journal_lines') IS NULL THEN
    RAISE EXCEPTION 'DR restore failed: journal_lines table missing';
  END IF;
END $$;

DO $$
DECLARE
  unbalanced INTEGER;
BEGIN
  SELECT COUNT(*) INTO unbalanced
  FROM (
    SELECT e.id
    FROM journal_entries e
    JOIN journal_lines l ON l.journal_entry_id = e.id
    GROUP BY e.id
    HAVING ABS(COALESCE(SUM(l.debit),0) - COALESCE(SUM(l.credit),0)) >= 0.01
  ) q;

  IF unbalanced > 0 THEN
    RAISE EXCEPTION 'DR restore failed: % unbalanced journal entries found', unbalanced;
  END IF;
END $$;
SQL

echo "[DR] PASS: backup restored inside the private PostgreSQL service and core financial invariants passed."
echo "[DR] RPO/RTO measurement must be recorded separately from this technical restore test."
