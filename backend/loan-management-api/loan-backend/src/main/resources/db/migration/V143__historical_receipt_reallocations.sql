CREATE TABLE historical_receipt_reallocations (
    receipt_id BIGINT PRIMARY KEY REFERENCES payment_transactions(id),
    journal_entry_id BIGINT NOT NULL UNIQUE REFERENCES journal_entries(id),
    as_of_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    penalty NUMERIC(19,2) NOT NULL CHECK (penalty >= 0),
    management_fee NUMERIC(19,2) NOT NULL CHECK (management_fee >= 0),
    interest NUMERIC(19,2) NOT NULL CHECK (interest >= 0),
    principal NUMERIC(19,2) NOT NULL CHECK (principal >= 0),
    extension_fee NUMERIC(19,2) NOT NULL CHECK (extension_fee >= 0),
    unapplied NUMERIC(19,2) NOT NULL CHECK (unapplied >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (amount = penalty + management_fee + interest + principal + extension_fee + unapplied)
);

CREATE FUNCTION reject_historical_receipt_reallocation_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Historical receipt reallocations are immutable';
END;
$$;

CREATE TRIGGER trg_historical_receipt_reallocation_immutable
BEFORE UPDATE OR DELETE ON historical_receipt_reallocations
FOR EACH ROW EXECUTE FUNCTION reject_historical_receipt_reallocation_change();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_app') THEN
        GRANT SELECT, INSERT ON historical_receipt_reallocations TO loansaas_app;
    END IF;
END;
$$;
