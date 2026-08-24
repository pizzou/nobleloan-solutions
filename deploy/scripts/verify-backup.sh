#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="${1:?Usage: verify-backup.sh <backup.sql.gz[.enc]>}"
[[ -f "$BACKUP_FILE" ]] || { echo "Backup file not found: $BACKUP_FILE"; exit 1; }

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

INPUT="$BACKUP_FILE"
if [[ "$BACKUP_FILE" == *.enc ]]; then
  : "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required for encrypted backups}"
  openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPTION_KEY \
    -in "$BACKUP_FILE" -out "$WORKDIR/backup.sql.gz"
  INPUT="$WORKDIR/backup.sql.gz"
fi

gzip -t "$INPUT"

# Verify that the dump contains the core financial schema. This is intentionally
# read-only; it does not touch the production database.
gunzip -c "$INPUT" | grep -Eq 'CREATE TABLE (public\.)?loans' || {
  echo "Backup verification failed: loans table was not found."
  exit 1
}

gunzip -c "$INPUT" | grep -Eq 'CREATE TABLE (public\.)?payments' || {
  echo "Backup verification failed: payments table was not found."
  exit 1
}

echo "[BACKUP VERIFY] PASS: archive is readable and contains core loan/payment schema."
