#!/usr/bin/env bash
# Verify the reviewed receipt correction on a new database restored from a backup.
set -euo pipefail
umask 077

backup=${1:?Usage: verify-historical-repayment.sh /absolute/path/to/backup.dump}
[[ "$backup" == /* && -s "$backup" ]] || { echo "Supply a nonempty absolute backup path." >&2; exit 1; }

cd /opt/nobleloan-solutions
revision=$(git rev-parse HEAD)

pg=loansaas-postgres
suffix="$(date -u +%Y%m%d%H%M%S)_$$"
test_db="nls_repayment_verify_${suffix}"
test_container="nls-repayment-verify-${suffix//_/-}"
test_image="nls-repayment-verify:${suffix}"
work=$(mktemp -d)
cleanup() {
  docker rm -f "$test_container" >/dev/null 2>&1 || true
  rm -rf -- "$work"
}
trap cleanup EXIT

project=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' loansaas-backend)
config_files=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.config_files"}}' loansaas-backend)
[[ -n "$project" && -n "$config_files" ]]
compose=(docker compose -p "$project")
IFS=, read -r -a files <<< "$config_files"
for file in "${files[@]}"; do compose+=(-f "$file"); done

mkdir "$work/source"
git archive "$revision" backend/loan-management-api/loan-backend | tar -x -C "$work/source"
docker build -t "$test_image" "$work/source/backend/loan-management-api/loan-backend"

docker exec "$pg" sh -c 'exec createdb -U "$POSTGRES_USER" "$1"' sh "$test_db"
echo "Restored verification database: $test_db"
docker exec -i "$pg" sh -c 'exec pg_restore --exit-on-error -U "$POSTGRES_USER" -d "$1"' sh "$test_db" < "$backup"

psql_test() {
  docker exec -i "$pg" sh -c 'exec psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' sh "$test_db"
}

cat > "$work/control.sql" <<'SQL'
SELECT md5(to_jsonb(receipt)::text) FROM payment_transactions receipt WHERE id = 5 AND loan_id = 54;
SELECT md5(to_jsonb(entry)::text) FROM journal_entries entry WHERE id = 306;
SELECT md5(string_agg(to_jsonb(line)::text, ',' ORDER BY line.id))
FROM journal_lines line WHERE journal_entry_id = 306;
SQL
original=$(psql_test < "$work/control.sql")
[[ $(printf '%s\n' "$original" | grep -cE '^[0-9a-f]{32}$') -eq 3 ]] || {
  echo "Backup is missing the expected receipt or journal." >&2
  exit 1
}

cat > "$work/correction.json" <<'JSON'
{
  "loanId": 54,
  "receiptId": 5,
  "opening": {
    "asOf": "2026-09-26",
    "maturity": "2026-09-26",
    "monthlyPenaltyRate": 10.00,
    "principal": 33500000.00,
    "penalty": 0.00,
    "interest": 6300000.00,
    "management": 6300000.00,
    "extension": 0.00
  },
  "charges": [],
  "cutoff": "2026-11-25",
  "historicalOpeningCutoff": "2026-11-12",
  "expectedPostedAssessment": 8870000.00
}
JSON
chmod 644 "$work/correction.json"
printf 'services:\n  backend:\n    image: %s\n' "$test_image" > "$work/image.yml"

run_correction() {
  local url="jdbc:postgresql://postgres:5432/$test_db"
  "${compose[@]}" -f "$work/image.yml" run --detach --no-deps --pull never --name "$test_container" \
    -e DB_URL="$url" -e FLYWAY_DB_URL="$url" -e DB_NAME="$test_db" \
    -e SPRING_DATASOURCE_URL="$url" -e SPRING_FLYWAY_URL="$url" \
    -e APP_HISTORICAL_REPAYMENT_ENABLED=true -e APP_HISTORICAL_REPAYMENT_INPUT=/tmp/historical-review.json \
    -e MAIL_ENABLED=false -e SMS_ENABLED=false -e CREDIT_BUREAU_ENABLED=false \
    -e COMPLIANCE_EXTERNAL_PROVIDER_ENABLED=false -e FLUTTERWAVE_ENABLED=false \
    -e MTN_MOMO_ENABLED=false -e AIRTEL_MONEY_ENABLED=false \
    -e APP_SCHEDULER_EOD_CRON=- -e OVERDUE_CRON=- -e FX_REFRESH_CRON=- \
    -e APP_SCHEDULER_PAYMENT_REMINDER_CRON=- -e APP_SCHEDULER_IDEMPOTENCY_CLEANUP_CRON=- \
    -v "$work/correction.json:/tmp/historical-review.json:ro" backend >/dev/null
  local attempt state
  for ((attempt = 1; attempt <= 90; attempt++)); do
    if docker exec "$test_container" wget -qO- -T 5 \
      http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"'; then
      docker rm -f "$test_container" >/dev/null
      return
    fi
    state=$(docker inspect -f '{{.State.Status}}' "$test_container")
    [[ "$state" == exited || "$state" == dead ]] && break
    sleep 5
  done
  echo "Verification backend did not become ready. Inspect $test_db before retrying." >&2
  docker logs --tail 70 "$test_container" >&2
  return 1
}

cat > "$work/assert.sql" <<'SQL'
DO $$
DECLARE
    correction_count integer;
    journal_count integer;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM loans WHERE id = 54
                   AND reference_number = 'NLS-000054'
                   AND outstanding_balance = 33500000.00
                   AND penalties_assessed = 6253333.33
                   AND penalties_paid = 5583333.33) THEN
        RAISE EXCEPTION 'Corrected loan balances differ from the reviewed amounts';
    END IF;
    SELECT COUNT(*) INTO correction_count FROM historical_receipt_reallocations
    WHERE receipt_id = 5 AND amount = 12100000.00 AND penalty = 5583333.33
      AND management_fee = 3258333.33 AND interest = 3258333.34 AND principal = 0;
    IF correction_count <> 1 THEN
        RAISE EXCEPTION 'Effective receipt allocation differs from the reviewed amounts';
    END IF;
    SELECT COUNT(*) INTO journal_count FROM journal_entries
    WHERE source_id = 'LOAN:54:RECEIPT:5' AND NOT reversed
      AND source_type IN ('HISTORICAL_RECEIPT_REALLOCATION', 'HISTORICAL_PENALTY_RESTATEMENT');
    IF journal_count <> 2 OR EXISTS (
        SELECT e.id FROM journal_entries e
        LEFT JOIN journal_lines l ON l.journal_entry_id = e.id
        WHERE e.source_id = 'LOAN:54:RECEIPT:5'
          AND e.source_type IN ('HISTORICAL_RECEIPT_REALLOCATION', 'HISTORICAL_PENALTY_RESTATEMENT')
        GROUP BY e.id
        HAVING COUNT(l.id) < 2 OR SUM(l.debit) <> SUM(l.credit)
    ) THEN
        RAISE EXCEPTION 'Correction journals are missing, duplicated, or unbalanced';
    END IF;
    IF EXISTS (
        SELECT 1 FROM journal_entries e
        JOIN journal_lines l ON l.journal_entry_id = e.id
        JOIN chart_of_accounts a ON a.id = l.account_id
        WHERE e.source_id = 'LOAN:54:RECEIPT:5'
          AND e.source_type IN ('HISTORICAL_RECEIPT_REALLOCATION', 'HISTORICAL_PENALTY_RESTATEMENT')
          AND a.code = '1000'
    ) THEN
        RAISE EXCEPTION 'Correction must not post cash';
    END IF;
END $$;
SELECT string_agg(id::text, ',' ORDER BY id)
FROM journal_entries WHERE source_id = 'LOAN:54:RECEIPT:5'
  AND source_type IN ('HISTORICAL_RECEIPT_REALLOCATION', 'HISTORICAL_PENALTY_RESTATEMENT');
SQL

echo "Running the correction against $test_db"
run_correction
first=$(psql_test < "$work/assert.sql")
[[ "$(psql_test < "$work/control.sql")" == "$original" ]]
echo "Re-running the same correction"
run_correction
second=$(psql_test < "$work/assert.sql")
[[ "$second" == "$first" && "$(psql_test < "$work/control.sql")" == "$original" ]]
echo "PASS: 670,000.00 RWF unpaid penalties, 33,500,000.00 RWF principal, balanced journals, original receipt unchanged, no duplicates."
echo "Verified journal IDs: $second. Restored database retained: $test_db"
