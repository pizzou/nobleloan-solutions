#!/usr/bin/env bash
# Automated weekly DR drill: create backup, verify it, restore into an isolated DB,
# validate schema/accounting invariants, and destroy the test DB.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
./deploy/scripts/backup.sh
LATEST="$(ls -1t backups/loansaas_nobleloansolutions_*.sql.gz* | head -1)"
./deploy/scripts/verify-backup.sh "$LATEST"
./deploy/scripts/test-restore.sh "$LATEST"
echo "[DR DRILL] PASS — latest encrypted backup was verified and restored in isolation."
