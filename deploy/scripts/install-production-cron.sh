#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CRON_FILE="${ROOT_DIR}/.production-cron"
cat > "$CRON_FILE" <<CRON
# Noble Loan Solutions production backup and DR verification
15 2 * * * cd $ROOT_DIR && BACKUP_ENVIRONMENT=production ./deploy/scripts/backup.sh >> /var/log/loansaas-backup.log 2>&1
45 3 * * 0 cd $ROOT_DIR && BACKUP_ENVIRONMENT=production ./deploy/scripts/dr-drill.sh >> /var/log/loansaas-dr.log 2>&1
CRON
crontab "$CRON_FILE"
echo "Installed production backup at 02:15 UTC daily and DR drill at 03:45 UTC Sundays."
