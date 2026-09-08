-- BNR/regulatory reporting performance indexes.
-- These indexes do not change accounting semantics or business rules.

CREATE INDEX IF NOT EXISTS idx_loans_bnr_org_branch_disbursed
    ON loans (organization_id, branch_id, disbursed_at);

CREATE INDEX IF NOT EXISTS idx_loans_bnr_org_borrower
    ON loans (organization_id, borrower_id);

CREATE INDEX IF NOT EXISTS idx_payment_schedules_loan_installment
    ON payment_schedules (loan_id, installment_number);

CREATE INDEX IF NOT EXISTS idx_journal_entries_bnr_org_date_id
    ON journal_entries (organization_id, entry_date, id);

CREATE INDEX IF NOT EXISTS idx_journal_lines_entry_account
    ON journal_lines (journal_entry_id, account_id);
