DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM payments
        WHERE transaction_id IS NOT NULL
          AND btrim(transaction_id) <> ''
        GROUP BY organization_id, transaction_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot create payment idempotency index: duplicate organization/transaction_id rows exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM journal_entries
        WHERE source_type IS NOT NULL
          AND source_id IS NOT NULL
        GROUP BY organization_id, source_type, source_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot create journal idempotency index: duplicate organization/source_type/source_id rows exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_org_transaction
    ON payments (organization_id, transaction_id)
    WHERE transaction_id IS NOT NULL AND btrim(transaction_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_journal_org_source
    ON journal_entries (organization_id, source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;