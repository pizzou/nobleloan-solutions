# Historical correction — safe execution procedure

**No automatic production changes.** The finance correction SQL is not a
Flyway migration and does not execute when the Spring Boot backend starts.

## What has been implemented

- Institution-wide maturity-based penalty and repayment algorithm, with
  regression tests and deterministic historical cash replay
- A read-only candidate calculation for all open loans
- A guarded loan NLS-000054 correction transaction that:
  - checks exact original borrower loan, immutable receipt, installment and GL state
  - marks the original transaction **reversed**, preserving original cash and components
  - posts a replacement dated immutable receipt with the corrected allocation
  - posts only balanced **noncash** adjusting journals (no second cash debit)
  - separates historical opening penalty correction (equity 3010) from
    postpayment penalty catch-up (income 4100)
  - updates loan and installment subledgers and checks their reconciliation
  - rejects a CLOSED November accounting period or newer transaction/accrual
  - rolls back by default and requires APPLY=true and APPROVAL_REF to commit

## Tested input assumptions — must be independently supported

For NLS-000054 (loan 54; **not a borrower-name assumption**): disbursed
2026-03-26, term six months, maturity 2026-09-26; penalty first chargeable
2026-09-30 at 10% legacy rate. Receipt transaction 5 of RWF 12.1m on
2026-11-19. Principal at maturity RWF 33.5m, unpaid contractual
interest/management RWF 6.3m each. No other cash transactions or penalty
accruals after the 2026-11-24 baseline. Original imported RWF 7.5m is
preserved as historical collected money.

Replayed receipt: penalty 5,583,333.33; interest 3,258,333.33; management
3,258,333.34; principal 0. Corrected cumulative penalty through Nov 24:
6,141,666.67; paid penalty 5,583,333.33; outstanding penalty 558,333.34.
Original total paid RWF 19.6m remains unchanged.

**If any snapshot, journal, date, balance, period or approved-evidence input is
different, STOP and regenerate the plan rather than editing the safety checks.**

## Verify on restored data, never on live data first

Backup source on the server:
`/root/nobleloan-backups/nobleloan-pre-penalty-deploy-20261124-232352.dump`

```bash
cd /opt/nobleloan-solutions || exit 1
BACKUP=/root/nobleloan-backups/nobleloan-pre-penalty-deploy-20261124-232352.dump
test -s "$BACKUP" || exit 1
df -h /var/lib/docker
docker exec loansaas-postgres sh -c \
  'createdb -U "$POSTGRES_USER" nls_penalty_reclass_audit_20261125'
docker exec -i loansaas-postgres sh -c \
  'pg_restore --exit-on-error --no-owner --no-acl -U "$POSTGRES_USER" -d nls_penalty_reclass_audit_20261125' \
  < "$BACKUP" || exit 1

git fetch origin fix/overdue-charges-before-principal-20261125 || exit 1
set -o pipefail
git show FETCH_HEAD:operations/financial-corrections/NLS-000054-historical-reclassify-guarded.sql \
  | docker exec -i loansaas-postgres sh -c \
  'exec psql -X -v ON_ERROR_STOP=1 -v APPLY=false -U "$POSTGRES_USER" -d nls_penalty_reclass_audit_20261125 -P pager=off'
```

**Expected:** simulated adjustment NOTICE followed by `ROLLBACK`.
The original restored data must remain unchanged. If any step fails,
do not turn on APPLY or alter the original source documents.

Before any live correction: perform a committed replay **on an isolated
restored database** with independent approval, validate resulting loan
receivables against 1100/1150/1160/1175 GL, verify the complete replacement
receipt journal trail, compare BNR/general/cash-flow reporting periods, and
take a fresh production backup. Confirm rollback before authorizing a live
transaction. Never automatically mass-edit other loans: earlier import
opening components and payment-date source history vary.

## Other affected loans

Use `audits/portfolio-maturity-penalty-recalculation-candidates-read-only.sql`
to identify loans whose principal is unchanged versus loans requiring
payment replay. NLS-000099 separately requires resolution of a
RWF 116,666.67 GL/subledger mismatch; **do not mask it as a penalty waiver**.
