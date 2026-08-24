
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RAW_FILE="$BACKUP_DIR/loansaas_$TIMESTAMP.sql.gz"
RETENTION_DAYS=${RETENTION_DAYS:-30}
BACKUP_ENVIRONMENT=${BACKUP_ENVIRONMENT:-development}

if [[ "$BACKUP_ENVIRONMENT" == "production" ]]; then
    : "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required for production backups}"
    : "${BACKUP_REMOTE_CMD:?BACKUP_REMOTE_CMD is required for production backups}"
fi

mkdir -p "$BACKUP_DIR"

echo "[BACKUP] Starting PostgreSQL backup..."
docker-compose exec -T postgres pg_dump \
    -U loansaas loansaas \
    | gzip > "$RAW_FILE"

FINAL_FILE="$RAW_FILE"
if [ -n "${BACKUP_ENCRYPTION_KEY:-}" ]; then
    openssl enc -aes-256-cbc -pbkdf2 -salt -pass env:BACKUP_ENCRYPTION_KEY \
        -in "$RAW_FILE" -out "$RAW_FILE.enc"
    rm -f "$RAW_FILE"
    FINAL_FILE="$RAW_FILE.enc"
    echo "[BACKUP] Encrypted with BACKUP_ENCRYPTION_KEY"
else
    echo "[BACKUP] WARNING: BACKUP_ENCRYPTION_KEY not set — backup contains unencrypted borrower"
    echo "[BACKUP]          PII (national IDs, phone numbers, etc. decrypted at pg_dump time is"
    echo "[BACKUP]          NOT how the app stores them, but a raw SQL dump of an encrypted column"
    echo "[BACKUP]          is still sensitive — anyone with this file plus APP_ENCRYPTION_KEY can"
    echo "[BACKUP]          decrypt it). Set BACKUP_ENCRYPTION_KEY before using this in production."
fi

echo "[BACKUP] Backup saved: $FINAL_FILE ($(du -sh "$FINAL_FILE" | cut -f1))"

if [ -n "${BACKUP_REMOTE_CMD:-}" ]; then
    echo "[BACKUP] Shipping off-site via BACKUP_REMOTE_CMD..."
    eval "$BACKUP_REMOTE_CMD \"$FINAL_FILE\""
else
    echo "[BACKUP] WARNING: BACKUP_REMOTE_CMD not set — this backup only exists on this host."
    echo "[BACKUP]          If this host is lost, this backup is lost with it. Set"
    echo "[BACKUP]          BACKUP_REMOTE_CMD to actually ship backups off-site."
fi

# Delete local backups older than retention period (off-site copies follow their own retention,
# set on whatever storage BACKUP_REMOTE_CMD ships to)
find "$BACKUP_DIR" -name "loansaas_*.sql.gz*" -mtime +$RETENTION_DAYS -delete
echo "[BACKUP] Cleaned up local backups older than $RETENTION_DAYS days"
echo "[BACKUP] Done!"
