#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CRON_FILE="${ROOT_DIR}/.production-cron"
[[ -f "${ROOT_DIR}/.env" ]] || { echo ".env is required before installing production cron jobs." >&2; exit 1; }
set -a; # shellcheck disable=SC1091
source "${ROOT_DIR}/.env"; set +a
[[ -n "${BACKUP_ENCRYPTION_KEY:-}" ]] || { echo "BACKUP_ENCRYPTION_KEY must be configured before installing backup cron." >&2; exit 1; }
[[ "${BACKUP_REMOTE_CMD:-}" == *"{file}"* ]] || { echo "BACKUP_REMOTE_CMD must contain {file} before installing backup cron." >&2; exit 1; }
[[ -n "${DOMAIN:-}" ]] || { echo "DOMAIN must be configured before installing SSL renewal cron." >&2; exit 1; }

cat > "$CRON_FILE" <<CRON
# Noble Loan Solutions production backup, DR verification, and TLS renewal
15 2 * * * cd $ROOT_DIR && BACKUP_ENVIRONMENT=production ./deploy/scripts/backup.sh >> /var/log/loansaas-backup.log 2>&1
45 3 * * 0 cd $ROOT_DIR && BACKUP_ENVIRONMENT=production ./deploy/scripts/dr-drill.sh >> /var/log/loansaas-dr.log 2>&1
15 4 * * 0 cd $ROOT_DIR && ./deploy/scripts/ssl-renew.sh >> /var/log/loansaas-ssl.log 2>&1
CRON
crontab "$CRON_FILE"
echo "Installed production backup at 02:15 UTC daily, DR drill at 03:45 UTC Sundays, and TLS renewal at 04:15 UTC Sundays."
