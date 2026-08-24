#!/usr/bin/env bash
# Noble Loan Solutions — encrypted PostgreSQL backup with integrity manifest.
set -euo pipefail
umask 077

BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP="$(date -u +"%Y%m%d_%H%M%S")"
DB_NAME="${DB_NAME:-loansaas_nobleloansolutions}"
DB_USER="${DB_USER:-loansaas}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
BACKUP_ENVIRONMENT="${BACKUP_ENVIRONMENT:-production}"
RAW_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"
FINAL_FILE="$RAW_FILE"

mkdir -p "$BACKUP_DIR"

if [[ "$BACKUP_ENVIRONMENT" == "production" ]]; then
  : "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required for production backups}"
  : "${BACKUP_REMOTE_CMD:?BACKUP_REMOTE_CMD is required for production backups}"
fi

command -v docker-compose >/dev/null || { echo "docker-compose is required" >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }
command -v gzip >/dev/null || { echo "gzip is required" >&2; exit 1; }

echo "[BACKUP] Starting consistent PostgreSQL dump: $DB_NAME"
docker-compose exec -T postgres pg_dump \
  --username="$DB_USER" \
  --dbname="$DB_NAME" \
  --no-owner --no-privileges --format=plain \
  | gzip -9 > "$RAW_FILE"

gzip -t "$RAW_FILE"

if [[ -n "${BACKUP_ENCRYPTION_KEY:-}" ]]; then
  openssl enc -aes-256-cbc -pbkdf2 -salt \
    -pass env:BACKUP_ENCRYPTION_KEY \
    -in "$RAW_FILE" -out "$RAW_FILE.enc"
  rm -f "$RAW_FILE"
  FINAL_FILE="$RAW_FILE.enc"
fi

SHA256="$(sha256sum "$FINAL_FILE" | awk '{print $1}')"
BYTES="$(stat -c '%s' "$FINAL_FILE")"
printf '%s  %s\n' "$SHA256" "$(basename "$FINAL_FILE")" > "$FINAL_FILE.sha256"
cat > "$FINAL_FILE.manifest" <<MANIFEST
backup_version=2
database=$DB_NAME
timestamp_utc=$TIMESTAMP
sha256=$SHA256
bytes=$BYTES
encrypted=$([[ "$FINAL_FILE" == *.enc ]] && echo true || echo false)
MANIFEST

echo "[BACKUP] Created: $FINAL_FILE"
echo "[BACKUP] SHA-256: $SHA256"

if [[ -n "${BACKUP_REMOTE_CMD:-}" ]]; then
  # BACKUP_REMOTE_CMD must contain a literal {file} placeholder. This avoids shell eval.
  if [[ "$BACKUP_REMOTE_CMD" != *"{file}"* ]]; then
    echo "BACKUP_REMOTE_CMD must contain {file}; refusing unsafe remote upload command" >&2
    exit 1
  fi
  for f in "$FINAL_FILE" "$FINAL_FILE.sha256" "$FINAL_FILE.manifest"; do
    cmd="${BACKUP_REMOTE_CMD//\{file\}/$f}"
    echo "[BACKUP] Off-site upload: $(basename "$f")"
    bash -c "$cmd"
  done
else
  [[ "$BACKUP_ENVIRONMENT" != "production" ]] || { echo "Production backup must be off-site" >&2; exit 1; }
fi

find "$BACKUP_DIR" -type f \( -name "${DB_NAME}_*.sql.gz" -o -name "${DB_NAME}_*.sql.gz.enc" -o -name "${DB_NAME}_*.sha256" -o -name "${DB_NAME}_*.manifest" \) -mtime "+$RETENTION_DAYS" -delete

echo "[BACKUP] PASS — encrypted=$( [[ "$FINAL_FILE" == *.enc ]] && echo yes || echo no ), offsite=$( [[ -n "${BACKUP_REMOTE_CMD:-}" ]] && echo yes || echo no )"
