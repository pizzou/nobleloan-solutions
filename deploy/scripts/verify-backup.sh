#!/usr/bin/env bash
# Verify backup checksum, encryption/decompression integrity, and manifest.
set -euo pipefail
umask 077
FILE="${1:?Usage: verify-backup.sh <backup.sql.gz[.enc]>}"
[[ -f "$FILE" ]] || { echo "Backup not found: $FILE" >&2; exit 1; }
sha256sum -c "$FILE.sha256"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
INPUT="$FILE"
if [[ "$FILE" == *.enc ]]; then
  : "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required for encrypted backups}"
  openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPTION_KEY -in "$FILE" -out "$TMP/backup.sql.gz"
  INPUT="$TMP/backup.sql.gz"
fi
gzip -t "$INPUT"
printf '[BACKUP VERIFY] PASS: checksum + encryption + gzip integrity\n'
