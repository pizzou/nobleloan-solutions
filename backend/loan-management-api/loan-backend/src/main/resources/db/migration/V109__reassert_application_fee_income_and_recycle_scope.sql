-- V109: bank-grade application-fee income reassertion and recycle-scope repair.
--
-- Application fees collected at disbursement are earned fee income. They are
-- not loan receivables/assets once collected. 4100 is therefore a CREDIT
-- balance INCOME account.
--
-- The application already posts:
--   DR 1100 Loans Receivable       gross principal
--   CR 1000 Cash                  net cash to borrower
--   CR 4100 Application Fee Income collected fee
--
-- Older deployments could have created 4100 with the wrong classification or
-- posted the fee against migration equity. This migration repairs the chart
-- classification and creates an immutable, balanced reclassification journal
-- for legacy disbursements where the old journal contains the fee amount in
-- migration equity but no 4100 income line.

UPDATE chart_of_accounts
SET type = CASE code
        WHEN '1000' THEN 'ASSET'
        WHEN '1100' THEN 'ASSET'
        WHEN '1150' THEN 'ASSET'
        WHEN '1160' THEN 'ASSET'
        WHEN '1170' THEN 'ASSET'
        WHEN '1175' THEN 'ASSET'
        WHEN '1200' THEN 'ASSET'
        WHEN '2000' THEN 'LIABILITY'
        WHEN '2100' THEN 'LIABILITY'
        WHEN '3000' THEN 'EQUITY'
        WHEN '3010' THEN 'EQUITY'
        WHEN '4000' THEN 'INCOME'
        WHEN '4100' THEN 'INCOME'
        WHEN '5000' THEN 'EXPENSE'
        WHEN '5100' THEN 'EXPENSE'
        WHEN '5200' THEN 'EXPENSE'
        WHEN '5201' THEN 'EXPENSE'
        WHEN '5202' THEN 'EXPENSE'
        WHEN '5203' THEN 'EXPENSE'
        WHEN '5204' THEN 'EXPENSE'
        WHEN '5205' THEN 'EXPENSE'
        WHEN '5206' THEN 'EXPENSE'
        WHEN '5207' THEN 'EXPENSE'
        WHEN '5208' THEN 'EXPENSE'
        WHEN '5209' THEN 'EXPENSE'
        WHEN '5210' THEN 'EXPENSE'
        WHEN '5211' THEN 'EXPENSE'
        WHEN '5212' THEN 'EXPENSE'
        WHEN '5213' THEN 'EXPENSE'
        WHEN '5214' THEN 'EXPENSE'
        WHEN '5215' THEN 'EXPENSE'
        ELSE type
    END,
    normal_balance = CASE code
        WHEN '1000' THEN 'DEBIT'
        WHEN '1100' THEN 'DEBIT'
        WHEN '1150' THEN 'DEBIT'
        WHEN '1160' THEN 'DEBIT'
        WHEN '1170' THEN 'DEBIT'
        WHEN '1175' THEN 'DEBIT'
        WHEN '1200' THEN 'CREDIT'
        WHEN '2000' THEN 'CREDIT'
        WHEN '2100' THEN 'CREDIT'
        WHEN '3000' THEN 'CREDIT'
        WHEN '3010' THEN 'CREDIT'
        WHEN '4000' THEN 'CREDIT'
        WHEN '4100' THEN 'CREDIT'
        WHEN '5000' THEN 'DEBIT'
        WHEN '5100' THEN 'DEBIT'
        WHEN '5200' THEN 'DEBIT'
        WHEN '5201' THEN 'DEBIT'
        WHEN '5202' THEN 'DEBIT'
        WHEN '5203' THEN 'DEBIT'
        WHEN '5204' THEN 'DEBIT'
        WHEN '5205' THEN 'DEBIT'
        WHEN '5206' THEN 'DEBIT'
        WHEN '5207' THEN 'DEBIT'
        WHEN '5208' THEN 'DEBIT'
        WHEN '5209' THEN 'DEBIT'
        WHEN '5210' THEN 'DEBIT'
        WHEN '5211' THEN 'DEBIT'
        WHEN '5212' THEN 'DEBIT'
        WHEN '5213' THEN 'DEBIT'
        WHEN '5214' THEN 'DEBIT'
        WHEN '5215' THEN 'DEBIT'
        ELSE normal_balance
    END
WHERE code IN (
    '1000','1100','1150','1160','1170','1175','1200',
    '2000','2100','3000','3010','4000','4100',
    '5000','5100','5200','5201','5202','5203','5204','5205',
    '5206','5207','5208','5209','5210','5211','5212','5213','5214','5215'
);

DO $$
DECLARE
    r RECORD;
    v_journal_id BIGINT;
    v_3010_id BIGINT;
    v_4100_id BIGINT;
    v_fee NUMERIC(19,2);
    v_source_id VARCHAR(100);
    v_reference VARCHAR(255);
    v_description TEXT;
BEGIN
    FOR r IN
        SELECT
            l.id AS loan_id,
            l.organization_id,
            l.branch_id,
            l.reference_number,
            ROUND(COALESCE(l.application_fee_paid, 0)::numeric, 2) AS fee,
            je.id AS disbursement_journal_id,
            je.entry_date,
            je.business_owner_only
        FROM loans l
        JOIN journal_entries je
          ON je.organization_id = l.organization_id
         AND je.source_type = 'LOAN_DISBURSEMENT'
         AND je.source_id = l.id::text
         AND COALESCE(je.reversed, false) = false
        WHERE COALESCE(l.application_fee_paid, 0) > 0
          AND l.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM journal_lines jl4100
              JOIN chart_of_accounts coa4100
                ON coa4100.id = jl4100.account_id
              WHERE jl4100.journal_entry_id = je.id
                AND coa4100.organization_id = l.organization_id
                AND coa4100.code = '4100'
                AND ROUND(COALESCE(jl4100.credit, 0)::numeric, 2) =
                    ROUND(COALESCE(l.application_fee_paid, 0)::numeric, 2)
          )
          AND EXISTS (
              SELECT 1
              FROM journal_lines jl3010
              JOIN chart_of_accounts coa3010
                ON coa3010.id = jl3010.account_id
              WHERE jl3010.journal_entry_id = je.id
                AND coa3010.organization_id = l.organization_id
                AND coa3010.code = '3010'
                AND ROUND(COALESCE(jl3010.credit, 0)::numeric, 2) =
                    ROUND(COALESCE(l.application_fee_paid, 0)::numeric, 2)
          )
    LOOP
        v_fee := r.fee;
        v_source_id := r.loan_id::text || ':APPLICATION_FEE_INCOME';
        v_reference := COALESCE(NULLIF(btrim(r.reference_number), ''), 'LOAN-' || r.loan_id::text);
        v_description := 'Reclassify collected application fee from migration equity to fee income — ' || v_reference;

        IF EXISTS (
            SELECT 1 FROM journal_entries
            WHERE organization_id = r.organization_id
              AND source_type = 'APPLICATION_FEE_INCOME_RECLASSIFICATION'
              AND source_id = v_source_id
        ) THEN
            CONTINUE;
        END IF;

        SELECT id INTO v_3010_id
        FROM chart_of_accounts
        WHERE organization_id = r.organization_id AND code = '3010';

        SELECT id INTO v_4100_id
        FROM chart_of_accounts
        WHERE organization_id = r.organization_id AND code = '4100';

        IF v_3010_id IS NULL OR v_4100_id IS NULL THEN
            RAISE EXCEPTION 'Required GL accounts 3010 and 4100 are missing for organization %', r.organization_id;
        END IF;

        INSERT INTO journal_entries (
            organization_id,
            branch_id,
            entry_date,
            source_type,
            source_id,
            reference,
            description,
            created_by,
            reversed,
            business_owner_only
        ) VALUES (
            r.organization_id,
            r.branch_id,
            r.entry_date,
            'APPLICATION_FEE_INCOME_RECLASSIFICATION',
            v_source_id,
            v_reference,
            v_description,
            'SYSTEM-MIGRATION-V108',
            false,
            COALESCE(r.business_owner_only, false)
        ) RETURNING id INTO v_journal_id;

        INSERT INTO journal_lines (journal_entry_id, account_id, debit, credit, description)
        VALUES
            (v_journal_id, v_3010_id, v_fee, 0, 'Remove collected application fee from migration equity — ' || v_reference),
            (v_journal_id, v_4100_id, 0, v_fee, 'Recognize collected application fee income — ' || v_reference);
    END LOOP;
END $$;

-- Keep recycle-bin accounting visibility complete for the new repair event.
CREATE OR REPLACE FUNCTION public.loan_journal_is_recycled(
    p_organization_id BIGINT,
    p_source_type TEXT,
    p_source_id TEXT,
    p_reference TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_source_id TEXT;
    v_loan_id BIGINT;
BEGIN
    IF p_organization_id IS NULL THEN
        RETURN FALSE;
    END IF;

    v_source_id := regexp_replace(trim(coalesce(p_source_id, '')), '^LOAN:', '');

    IF upper(trim(coalesce(p_source_type, ''))) = 'APPLICATION_FEE_INCOME_RECLASSIFICATION'
       AND v_source_id ~ '^[0-9]+:APPLICATION_FEE_INCOME$' THEN
        v_loan_id := split_part(v_source_id, ':', 1)::BIGINT;
        IF EXISTS (
            SELECT 1 FROM loans l
            WHERE l.id = v_loan_id
              AND l.organization_id = p_organization_id
              AND l.deleted_at IS NOT NULL
        ) OR EXISTS (
            SELECT 1 FROM loan_deletion_tombstones t
            WHERE t.loan_id = v_loan_id
              AND t.organization_id = p_organization_id
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    IF v_source_id ~ '^[0-9]+$'
       AND upper(trim(coalesce(p_source_type, ''))) IN (
            'LOAN_DISBURSEMENT','LOAN_PAYMENT','LOAN_EXTENSION_FEE',
            'LOAN_EXTENSION_FEE_COLLECTION','PENALTY_ACCRUAL','INTEREST_ACCRUAL',
            'MANAGEMENT_FEE_ACCRUAL','SCHEDULED_INTEREST_ACCRUAL',
            'SCHEDULED_MANAGEMENT_FEE_ACCRUAL','CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
            'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL','HISTORICAL_LOAN_OPENING',
            'LEGACY_LOAN_OPENING','LEGACY_LOAN_RECONCILIATION',
            'LEGACY_LOAN_OPENING_DATE_REPAIR','WRITE_OFF'
       ) THEN
        v_loan_id := v_source_id::BIGINT;
        IF EXISTS (
            SELECT 1 FROM loans l
            WHERE l.id = v_loan_id AND l.organization_id = p_organization_id AND l.deleted_at IS NOT NULL
        ) OR EXISTS (
            SELECT 1 FROM loan_deletion_tombstones t
            WHERE t.loan_id = v_loan_id AND t.organization_id = p_organization_id
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    IF v_source_id ~ '^[0-9]+$'
       AND upper(trim(coalesce(p_source_type, ''))) IN ('PAYMENT_RECEIVED','REFUND_PAYMENT','PAYMENT_REFUND','PAYMENT_REVERSAL') THEN
        IF EXISTS (
            SELECT 1
            FROM payments p JOIN loans l ON l.id = p.loan_id
            WHERE p.id = v_source_id::BIGINT
              AND l.organization_id = p_organization_id
              AND l.deleted_at IS NOT NULL
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    IF p_reference IS NOT NULL AND btrim(p_reference) <> '' THEN
        IF EXISTS (
            SELECT 1 FROM loans l
            WHERE l.organization_id = p_organization_id
              AND l.reference_number = btrim(p_reference)
              AND l.deleted_at IS NOT NULL
        ) OR EXISTS (
            SELECT 1 FROM loan_deletion_tombstones t
            WHERE t.organization_id = p_organization_id
              AND t.reference_number = btrim(p_reference)
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    RETURN FALSE;
END;
$$;
