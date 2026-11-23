# Noble Loan Solutions — consolidated release acceptance

**Target branch:** `security/nextjs-15.5.28`  
**Release policy:** Verify all workstreams together. No production data reset, loan deletion, automatic journal repair, or reimport.

This is an acceptance checklist and evidence register, **not** a certification or a claim that tests have been run.

## Gate 0 — Freeze and rollback safety

- [ ] Confirm clean Git working tree and record exact Git SHA.
- [ ] Confirm backup completed; verify archive is nonempty and independently restorable.
- [ ] Record existing backend/frontend image IDs and container health.
- [ ] Record baseline loan counts, imported/current split, payment transactions, GL journals, balances and sampled reports.
- [ ] Do not execute `docker compose down -v`, `git clean`, database resets or automated ledger updates.

## Gate 1 — Automated source and build tests

Automated GitHub Actions: `.github/workflows/consolidated-verification.yml`.
- [ ] Backend `mvn clean verify` succeeds with no test failures.
- [ ] Frontend `npm ci`, dependency/security verifiers, `npm run typecheck` and `npm run build` pass.
- [ ] Payment receipt query tests and immutable per-transaction journal tests pass.
- [ ] Flyway migrations validate in isolated PostgreSQL integration tests.
- [ ] No unmet CI checks or warnings requiring remediation.

## Gate 2 — Loan lifecycle (use disposable staging loans, never production borrower data)

- [ ] Create internal and public borrower; verify required spouse and KYC documents are conditional and enforced.
- [ ] Save draft application; approve/reject; check independent approval policies and unauthorized role denial.
- [ ] Disburse with actual prior disbursement date and verify first due date, application fee, net cash and GL entries.
- [ ] Verify permitted users can disburse and Business Owner cannot.
- [ ] Record 2 partial repayments against **one installment** and verify TWO distinct `PAYMENT_RECEIVED` journal sources (`PAYTX:<id>`) and exact cash debits.
- [ ] Retry an idempotency key; verify no second receipt, loan allocation or journal.
- [ ] Test late fee: original contractual rate on legacy loans, new-loan policy, 3 full calendar grace days, no double accrual.
- [ ] Request historical loan correction; independently approve; verify allocations, schedules, GL, refunds and audit trail.
- [ ] Recycle/restore loan; confirm restricted views and retention of accounting audit history.

## Gate 3 — Historical import and finance

- [ ] Preview and import representative legacy loan statuses and partial payments in isolated staging database.
- [ ] Verify unique sequential NLS loan references and duplicate prevention.
- [ ] Preserve Excel application fee and verified oldest unpaid date.
- [ ] Verify legacy opening amounts without inventing historical `Payment` rows.
- [ ] Compare principal, interest, management fees, extension fees, penalties and application fees to authoritative source.
- [ ] Confirm GL source 1000, 1100, 1150, 1160, 1170, 1175 and 1180 reconciles appropriately.
- [ ] Review opening funding/cash balance separately; do not fabricate prior funding transactions.
- [ ] Run `deploy/scripts/payment-journal-audit.sql` READ ONLY. Historical rows without PAYTX IDs are *investigations*, not automatic corrections.

### Read-only production journal diagnostic

```bash
cd /opt/nobleloan-solutions || exit 1
docker exec -i loansaas-postgres \
  psql -X -v ON_ERROR_STOP=1 -U loansaas \
  -d loansaas_nobleloansolutions -f - \
  < deploy/scripts/payment-journal-audit.sql
```

Review output without making journal updates. Because the new `PAYTX:` scheme did not exist historically, old posted transactions may appear unmatched even when they have a valid installment-based journal. Examine legacy references first.

## Gate 4 — Reports and regulatory

- [ ] Select identical organization, owner visibility, branch, date range and as-of date across each report.
- [ ] Compare Dashboard collected-this-month to dated receipts and documented application-fee receipts.
- [ ] Compare General Report loan repayments, total cash, outstanding principal, and receivables to authoritative sources.
- [ ] Compare BNR outstanding principal, PAR, overdue counts, collected cash and recoveries to portfolio and payment events.
- [ ] Compare P&L recognized income to accrued GL balances; don't equate lifetime cash with period income.
- [ ] Verify Excel, PDF and CSV exports (formulas, headers, dates, data escaping) and independent CRB formatting.
- [ ] Confirm missing historical payment dates do not appear as current-month cash.

## Gate 5 — Authentication, authorization and frontend

- [ ] Verify login, OTP, logout, password reset, lockout and session expiry.
- [ ] Test owner-only loans with Business Owner, Admin, Loan Officer, Accountant and Auditor accounts.
- [ ] Verify an authenticated user cannot reach another organization’s loans or files by direct URL or API.
- [ ] Check borrower/loan/payment/approval/report/download buttons and routes, loading/errors, filters and pagination.
- [ ] Verify notifications list, unread count, mark read, dismiss all, and refresh persistence.
- [ ] Run security-smoke and production-acceptance scripts with dedicated test credentials.
- [ ] Confirm CORS, CSRF protections, API authorization, audit logging, health and rate limiting.

## Gate 6 — External providers and operations

- [ ] Certify real credit bureau and KYC/AML providers; tests must not treat a mock as a production provider.
- [ ] Validate payment provider webhook signature, duplicate event suppression, receipt idempotency, and failed delivery.
- [ ] Test disaster-recovery restore, backup retention, low-memory application startup and simultaneous user requests.
- [ ] Require formal user acceptance from business owner/accounting/loan operations before production release.

## Final go/no-go

**GO** only if all mandatory automated checks pass, source financial balances reconcile, security/provider requirements are satisfied, and remaining known limitations are documented and accepted.

**NO-GO** if a build fails, two receipts share one GL journal, GL cash does not reconcile, unauthorized users can view private loans, opening balances lack evidence, or a production-critical provider is unavailable.

After final validation, record commit SHA, Docker image IDs, database backup path, test logs, reconciliation findings and signoff. Deploy only tested images; verify health and representative reports after rollout.
