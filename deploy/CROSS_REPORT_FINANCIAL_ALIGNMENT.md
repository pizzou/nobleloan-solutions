# Noble Loan Solutions — cross-report financial alignment

**Status:** source review and read-only database audit designed; monetary differences are NOT approved for adjustment.  
**Scope:** Accounting, General Report, BNR Summary, BNR Financial Statement, Loan Dashboard and Loan Portfolio.

## Evidence verified from the live system on 2026-11-24

- 134 nondeleted loan records: 129 imported, 5 system-created; 131 originated by status (ACTIVE, OVERDUE, PAID); the other 3 are PENDING 1 and REJECTED 2.
- 381 active GL journals; 38 LEGACY_LOAN_OPENING journals, all individually balanced and linked to imported loans.
- Opening portfolio DR 1100: 195,350,000; DR 1150: 28,705,000; DR 1160: 28,667,500; CR 3010: 252,722,500 RWF. Historical opening is NOT today's loan balance or cash.
- Current reported GL receivables: 1100 222,200,000; 1150 26,230,000; 1160 26,192,500; 1175 20,811,960.22; total 295,434,460.22 RWF; screenshot operational receivables 306,234,466.22 RWF, apparent 10,800,006 difference. Compare identical as-of dates, organizations and owner-only scope before attributing.
- GL 1000 shows net CR 20,430,000 RWF. Do not fabricate owner funding to mask missing actual cash source history.

## Source review and comparison rules

| Metric | Code/authoritative basis | Valid equality |
|---|---|---|
| Current principal | DashboardService current eligible funded loans; LoanRepository sumOutstandingBalance; RegulatoryReportingService BNR current portfolio | Compare same org/owner visibility/branch and as-of date with GL 1100, after reconciliation of historical adjustments |
| Interest, management, extension, penalty and unpaid application fees | Current Loan component balances vs GL 1150/1160/1170/1175/1180 | Reconcile component by component, not just grand total |
| Lifetime gross disbursements | DashboardService Loan.disbursedAmount | NOT equal to current outstanding principal or BNR selected-period disbursements |
| Lifetime recorded collections | DashboardService Loan.totalPaid + Loan.applicationFeePaid, includes legacy opening totals | NOT equal to dated GL cash receipts or BNR selected-period repayments |
| Historical application fee income | GL reclassification from 3010 to 4100 and specific disbursement journals | Do not replay historical cash or treat reclassification as newly received cash |
| Period P&L | GL income/expense journal entry dates | Compare General Report and BNR financial statements using same period and same visibility |
| Cash flow | Actual dated GL 1000 transactions | NOT equal to imported loan historical cumulative paid figures; no dated receipts may exist for imported pre-system collections |
| BNR financial statement loan portfolio statistics | BnrFinancialStatementService belongsToReportingPeriod selects loans with disbursedAt or disbursedAtTimestamp in selected period and sums original Loan.amount | This is a PERIOD ORIGINATION summary, not outstanding principal; imported loans lacking verified disbursement timestamps are not counted by that metric |
| Current BNR portfolio summary | RegulatoryReportingService fetchPortfolio(asOf) using LoanRepository.findVisiblePortfolioAsOf; current Loan component balances | Historical "as-of" snapshot requires historical loan balance reconstruction. Do not claim older dates are independently accurate from current loan fields |

## Source findings and hazards

1. Accounting page's top 5 operational KPIs are loaded from `loanApi.dashboard()`, not from account-date-filtered accounting endpoints. The UI correction at commit `3389b07` clarifies lifetime/current semantics and warns when GL receivables and operational receivables differ.
2. General Report page fetches lifetime dashboard data separately from `/reports/accounting/{organizationId}?from=...&to=...`; avoid misleading period labels on lifetime cards.
3. BNR financial portfolio statistics omit loans with unknown disbursement timestamps. Do not silently substitute created_at or import date: show as undated historical population with an explicit non-attributable bucket if a period-originations report requires it.
4. Historical opening 38 journals are correct structurally: 15 ACTIVE + 22 OVERDUE + 1 PAID; remaining 91 PAID have no opening journal. This does not certify Excel values or later payment allocation.
5. Credit Bureau provider missing live URL/API key is a separate integration problem, not an explanation of the accounting difference.
6. Never run a posting reconciliation job or a synthetic journal solely because a dashboard amount differs from GL.

## Database verification

Run `deploy/scripts/cross-report-receivables-audit.sql` READ ONLY against the intended production database. This compares current funded loan components, owner visibility, status, journal source breakdown and paid loans with residual balances. Record result before any code or journal correction.

## Release gate

1. Audit output must identify the exact component(s) of any mismatch; no assumption that GL or Loan is authoritative.
2. Verify journal date vs period/report visibility on the actual database.
3. Resolve all confirmed formula and classification defects in code, with tests on an independent PostgreSQL database.
4. Check General Report, Loan Portfolio, BNR Summary, BNR Financial Statements and Accounting with identical scope, period and as-of definitions.
5. Confirm independent restorable production DB backup and keep existing loans/journals unchanged until correction entries are supported and approved.
