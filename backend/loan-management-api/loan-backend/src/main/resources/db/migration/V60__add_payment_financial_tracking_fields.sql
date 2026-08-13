-- V60__add_payment_financial_tracking_fields.sql

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS principal_component NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS interest_component NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS management_fee_component NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS amount_paid NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS scheduled_interest NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS scheduled_management_fee NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_interest_due NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_interest_remaining NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_management_fee_due NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_management_fee_remaining NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS interest_calculation_date TIMESTAMP;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS penalty NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS penalty_paid NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS waived_amount NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS outstanding_after NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS paid BOOLEAN;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS due_date DATE;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS paid_date DATE;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(255);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(255);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS gateway_response TEXT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS channel VARCHAR(50);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS is_late BOOLEAN;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS days_late INTEGER;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS status VARCHAR(30);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;