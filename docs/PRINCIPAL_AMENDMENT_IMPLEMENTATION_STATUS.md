# Unified loan corrections: implementation status

This branch introduces a common request/review workflow for imported and system-created loans. A request can propose a corrected recorded principal, disbursement date, interest rate, management-fee rate, or a combination of these.

## Implemented

- V139 records the original and proposed principal, disbursement date, interest and management fee rates, disbursement and repayment snapshots, correction type, evidence, reason, requester and independent reviewer.
- Request endpoint: `POST /api/loans/{loanId}/principal-amendments`.
- History endpoint: `GET /api/loans/{loanId}/principal-amendments`.
- Owner inbox: `GET /api/principal-amendments/pending`.
- Independent owner approve/reject endpoints.
- Request and review screens at `/dashboard/loans/{id}/principal-amendments` and `/dashboard/principal-amendments` (internal routes retained for compatibility; the UI is a general loan-correction workflow).
- Approval marks the amendment **APPROVED_FOR_RECONCILIATION**, not **APPLIED**.
- Requests and decisions are audited. The original loan and historical cash transactions remain untouched.

## Not implemented; do not treat this as complete financial correction

There is deliberately **no financial execution endpoint** for active, overdue, defaulted or paid loans yet. A Business Owner can review and approve the proposed terms, but that approval is explicitly marked `APPROVED_FOR_RECONCILIATION` and must not be represented as a completed balance amendment.
Before an amendment may be applied, the system must reconcile existing payment allocations, installment schedules, contractual accrued fees, cash disbursement confirmation, general ledger entries and BNR/CRB reporting. `RECORDING_ERROR` must not trigger a new transfer, while `ADDITIONAL_DISBURSEMENT` must never create a receivable until verified funds were actually sent. Historical journal entries and payment records must remain immutable.

Do not deploy this branch as a complete principal correction solution or manually set amendment statuses to `APPLIED`.

## Build verification

The current scratch environment cannot download Maven Central dependencies; the new code requires `mvn clean verify` and migration verification against an isolated PostgreSQL database before production deployment.
