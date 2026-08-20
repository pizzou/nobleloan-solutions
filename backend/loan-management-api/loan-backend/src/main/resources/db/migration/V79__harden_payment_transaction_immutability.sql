-- V78: ensure all financial/provider metadata on payment_transactions is immutable.

CREATE OR REPLACE FUNCTION protect_payment_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Payment transactions are immutable; create a reversal instead';
    END IF;

    IF TG_OP='UPDATE' THEN
        IF NEW.id<>OLD.id
           OR NEW.organization_id<>OLD.organization_id
           OR NEW.loan_id<>OLD.loan_id
           OR NEW.installment_id IS DISTINCT FROM OLD.installment_id
           OR NEW.transaction_reference<>OLD.transaction_reference
           OR NEW.amount<>OLD.amount
           OR NEW.penalty_component IS DISTINCT FROM OLD.penalty_component
           OR NEW.interest_component IS DISTINCT FROM OLD.interest_component
           OR NEW.principal_component IS DISTINCT FROM OLD.principal_component
           OR NEW.management_fee_component IS DISTINCT FROM OLD.management_fee_component
           OR NEW.extension_fee_component IS DISTINCT FROM OLD.extension_fee_component
           OR NEW.unapplied_amount IS DISTINCT FROM OLD.unapplied_amount
           OR NEW.payment_method IS DISTINCT FROM OLD.payment_method
           OR NEW.channel IS DISTINCT FROM OLD.channel
           OR NEW.provider IS DISTINCT FROM OLD.provider
           OR NEW.currency IS DISTINCT FROM OLD.currency
           OR NEW.external_reference IS DISTINCT FROM OLD.external_reference
           OR NEW.gateway_status IS DISTINCT FROM OLD.gateway_status
           OR NEW.notes IS DISTINCT FROM OLD.notes
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'Payment transactions are immutable';
        END IF;

        IF OLD.reversed AND (
            NOT NEW.reversed
            OR NEW.reversed_at IS DISTINCT FROM OLD.reversed_at
            OR NEW.reversal_reason IS DISTINCT FROM OLD.reversal_reason
            OR NEW.reversal_reference IS DISTINCT FROM OLD.reversal_reference
        ) THEN
            RAISE EXCEPTION 'A reversed payment transaction cannot be changed again';
        END IF;

        IF NOT OLD.reversed AND NEW.reversed AND NEW.status <> 'REVERSED' THEN
            RAISE EXCEPTION 'A reversed payment transaction must have status REVERSED';
        END IF;

        IF NEW.status IS DISTINCT FROM OLD.status
           AND NOT (OLD.status='POSTED' AND NEW.status='REVERSED' AND NEW.reversed) THEN
            RAISE EXCEPTION 'Payment transaction status is immutable except for reversal';
        END IF;
    END IF;

    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_payment_transaction_immutable ON payment_transactions;
CREATE TRIGGER trg_payment_transaction_immutable
BEFORE UPDATE OR DELETE ON payment_transactions
FOR EACH ROW EXECUTE FUNCTION protect_payment_transaction();
