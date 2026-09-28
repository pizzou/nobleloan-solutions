#!/usr/bin/env bash
# Automated weekly DR drill: create backup, verify it, restore into an isolated DB,
# validate schema/accounting invariants, and destroy the test DB.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
if [[ -f .env ]]; then set -a; # shellcheck disable=SC1091
  source .env
  set +a
fi
BACKUP_ENVIRONMENT="${BACKUP_ENVIRONMENT:-production}" ./deploy/scripts/backup.sh
LATEST="$(find backups -maxdepth 1 -type f \
  \( -name 'loansaas_nobleloansolutions_*.sql.gz' -o -name 'loansaas_nobleloansolutions_*.sql.gz.enc' \) \
  -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
[[ -n "$LATEST" && -f "$LATEST" ]] || { echo "[DR DRILL] No backup artifact found." >&2; exit 1; }
./deploy/scripts/verify-backup.sh "$LATEST"
./deploy/scripts/test-restore.sh "$LATEST"
echo "[DR DRILL] PASS — latest encrypted backup was verified and restored in isolation."
