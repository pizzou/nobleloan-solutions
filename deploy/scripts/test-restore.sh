
set -euo pipefail

BACKUP_FILE="${1:?Usage: $0 <backup.sql.gz[.enc]>}"
[[ -f "$BACKUP_FILE" ]] || { echo "[DR] Backup file not found: $BACKUP_FILE" >&2; exit 1; }

command -v psql >/dev/null 2>&1 || { echo "[DR] psql is required." >&2; exit 1; }
command -v createdb >/dev/null 2>&1 || { echo "[DR] createdb is required." >&2; exit 1; }
command -v dropdb >/dev/null 2>&1 || { echo "[DR] dropdb is required." >&2; exit 1; }
command -v gzip >/dev/null 2>&1 || { echo "[DR] gzip is required." >&2; exit 1; }

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
DR_TEST_DB="${DR_TEST_DB:-loansaas_nobleloansolutions_restore_test_$(date +%Y%m%d_%H%M%S)_$$}"

WORKDIR="$(mktemp -d)"
cleanup() {
  rm -rf "$WORKDIR"
  dropdb --if-exists --host="$PGHOST" --port="$PGPORT" --username="$PGUSER" "$DR_TEST_DB" >/dev/null 2>&1 || true
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

echo "[DR] Creating isolated restore database: $DR_TEST_DB"
createdb --host="$PGHOST" --port="$PGPORT" --username="$PGUSER" "$DR_TEST_DB"

echo "[DR] Restoring backup into isolated database..."
gunzip -c "$INPUT" | psql \
  --host="$PGHOST" \
  --port="$PGPORT" \
  --username="$PGUSER" \
  --dbname="$DR_TEST_DB" \
  --set ON_ERROR_STOP=1 \
  >/dev/null

echo "[DR] Running structural checks..."
psql --host="$PGHOST" --port="$PGPORT" --username="$PGUSER" --dbname="$DR_TEST_DB" --set ON_ERROR_STOP=1 <<'SQL'
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

echo "[DR] PASS: backup restored successfully into isolated database and core financial invariants passed."
echo "[DR] RPO/RTO measurement must be recorded separately from this technical restore test."
