-- Performance indexes used by tenant-scoped dashboards and payment history.
CREATE INDEX IF NOT EXISTS idx_loans_org_status_created ON loans(organization_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_org_paid_date ON payments(organization_id,paid_date DESC);
CREATE INDEX IF NOT EXISTS idx_payments_org_due_status ON payments(organization_id,due_date,status);
CREATE INDEX IF NOT EXISTS idx_borrowers_org_created ON borrowers(organization_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_org_timestamp ON audit_logs(organization_id,timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_journal_org_date ON journal_entries(organization_id,entry_date DESC);

-- Double-entry invariant: every journal entry must have >=2 lines and equal debits/credits.
CREATE OR REPLACE FUNCTION enforce_balanced_journal_entry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE debit_total NUMERIC; credit_total NUMERIC; line_count INTEGER;
BEGIN
    SELECT COUNT(*), COALESCE(SUM(debit),0), COALESCE(SUM(credit),0)
      INTO line_count, debit_total, credit_total
      FROM journal_lines WHERE journal_entry_id = COALESCE(NEW.journal_entry_id, OLD.journal_entry_id);
    IF line_count < 2 OR round(debit_total::numeric,2) <> round(credit_total::numeric,2) THEN
        RAISE EXCEPTION 'Journal entry % is not balanced: lines=%, debit=%, credit=%', COALESCE(NEW.journal_entry_id,OLD.journal_entry_id),line_count,debit_total,credit_total;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;
DROP TRIGGER IF EXISTS trg_journal_balance ON journal_lines;
CREATE CONSTRAINT TRIGGER trg_journal_balance AFTER INSERT OR UPDATE OR DELETE ON journal_lines DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_balanced_journal_entry();

-- Financial transaction rows are append-only. A reversal is represented by the existing
-- reversed/reversed_at/reversal_reference fields, never by rewriting the original amount.
CREATE OR REPLACE FUNCTION protect_payment_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payment transactions are immutable; create a reversal instead'; END IF;
    IF TG_OP='UPDATE' THEN
        IF NEW.id<>OLD.id OR NEW.organization_id<>OLD.organization_id OR NEW.loan_id<>OLD.loan_id
           OR NEW.installment_id IS DISTINCT FROM OLD.installment_id
           OR NEW.transaction_reference<>OLD.transaction_reference
           OR NEW.amount<>OLD.amount
           OR NEW.penalty_component IS DISTINCT FROM OLD.penalty_component
           OR NEW.interest_component IS DISTINCT FROM OLD.interest_component
           OR NEW.principal_component IS DISTINCT FROM OLD.principal_component
           OR NEW.unapplied_amount IS DISTINCT FROM OLD.unapplied_amount
           OR NEW.payment_method IS DISTINCT FROM OLD.payment_method
           OR NEW.channel IS DISTINCT FROM OLD.channel
           OR NEW.notes IS DISTINCT FROM OLD.notes
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'Payment transactions are immutable';
        END IF;
        IF OLD.reversed AND (NOT NEW.reversed OR NEW.reversed_at IS DISTINCT FROM OLD.reversed_at OR NEW.reversal_reason IS DISTINCT FROM OLD.reversal_reason OR NEW.reversal_reference IS DISTINCT FROM OLD.reversal_reference) THEN
            RAISE EXCEPTION 'A reversed payment transaction cannot be changed again';
        END IF;
        IF NOT OLD.reversed AND NEW.reversed AND NEW.status <> 'REVERSED' THEN
            RAISE EXCEPTION 'A reversed payment transaction must have status REVERSED';
        END IF;
        IF NEW.status IS DISTINCT FROM OLD.status AND NOT (OLD.status='POSTED' AND NEW.status='REVERSED' AND NEW.reversed) THEN
            RAISE EXCEPTION 'Payment transaction status is immutable except for reversal';
        END IF;
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_payment_transaction_immutable ON payment_transactions;
CREATE TRIGGER trg_payment_transaction_immutable BEFORE UPDATE OR DELETE ON payment_transactions FOR EACH ROW EXECUTE FUNCTION protect_payment_transaction();

-- Journal entries are immutable except for the explicit reversed flag.
CREATE OR REPLACE FUNCTION protect_journal_entry() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Journal entries are immutable; reverse them instead'; END IF;
    IF NEW.organization_id<>OLD.organization_id OR NEW.entry_date<>OLD.entry_date OR NEW.reference IS DISTINCT FROM OLD.reference
       OR NEW.source_type IS DISTINCT FROM OLD.source_type OR NEW.source_id IS DISTINCT FROM OLD.source_id
       OR NEW.description IS DISTINCT FROM OLD.description OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.created_at<>OLD.created_at THEN RAISE EXCEPTION 'Journal entries are immutable'; END IF;
    IF OLD.reversed AND NOT NEW.reversed THEN RAISE EXCEPTION 'A reversed journal entry cannot be reopened'; END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_journal_entry_immutable ON journal_entries;
CREATE TRIGGER trg_journal_entry_immutable BEFORE UPDATE OR DELETE ON journal_entries FOR EACH ROW EXECUTE FUNCTION protect_journal_entry();
CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_org_key ON idempotency_keys(organization_id,idempotency_key);
