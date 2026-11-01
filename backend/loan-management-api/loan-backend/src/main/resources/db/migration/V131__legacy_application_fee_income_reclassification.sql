-- V131: recognize application fees already collected for legacy loans imported
-- after V108. These fees are imported from the actual Excel application_fee_paid
-- amount (not recalculated from the modern percentage). No cash movement is
-- replayed: the journal reclassifies opening migration equity to fee income.
-- The source ID matches AccountingService.postHistoricalApplicationFeeIncome().

DO $$
DECLARE
    r RECORD;
    v_journal_id BIGINT;
    v_equity_id BIGINT;
    v_income_id BIGINT;
    v_source_id VARCHAR(100);
    v_reference VARCHAR(255);
    v_fee NUMERIC(19,2);
    v_entry_date DATE;
BEGIN
    FOR r IN
        SELECT l.id AS loan_id,
               l.organization_id,
               l.branch_id,
               l.reference_number,
               ROUND(COALESCE(l.application_fee_paid, 0)::numeric, 2) AS fee,
               COALESCE(l.disbursed_at::date, l.start_date, l.created_at::date, CURRENT_DATE) AS entry_date,
               COALESCE(l.business_owner_only, false) AS business_owner_only
        FROM public.loans l
        WHERE l.deleted_at IS NULL
          AND (COALESCE(l.imported, false) = true OR l.import_batch_id IS NOT NULL)
          AND ROUND(COALESCE(l.application_fee_paid, 0)::numeric, 2) > 0
          AND NOT EXISTS (
              SELECT 1
              FROM public.journal_entries je
              WHERE je.organization_id = l.organization_id
                AND je.source_type = 'APPLICATION_FEE_INCOME_RECLASSIFICATION'
                AND je.source_id = l.id::text || ':APPLICATION_FEE_INCOME'
          )
          AND NOT EXISTS (
              SELECT 1
              FROM public.journal_entries disb
              JOIN public.journal_lines fee_line ON fee_line.journal_entry_id = disb.id
              JOIN public.chart_of_accounts fee_account ON fee_account.id = fee_line.account_id
              WHERE disb.organization_id = l.organization_id
                AND disb.source_type = 'LOAN_DISBURSEMENT'
                AND disb.source_id = l.id::text
                AND COALESCE(disb.reversed, false) = false
                AND fee_account.organization_id = l.organization_id
                AND fee_account.code = '4100'
                AND ROUND(COALESCE(fee_line.credit, 0)::numeric, 2)
                    >= ROUND(COALESCE(l.application_fee_paid, 0)::numeric, 2)
          )
        ORDER BY l.organization_id, l.id
    LOOP
        v_fee := r.fee;
        v_source_id := r.loan_id::text || ':APPLICATION_FEE_INCOME';
        v_reference := COALESCE(NULLIF(btrim(r.reference_number), ''), 'LOAN-' || r.loan_id::text);
        v_entry_date := r.entry_date;

        SELECT id INTO v_equity_id
        FROM public.chart_of_accounts
        WHERE organization_id = r.organization_id AND code = '3010';

        SELECT id INTO v_income_id
        FROM public.chart_of_accounts
        WHERE organization_id = r.organization_id AND code = '4100';

        IF v_equity_id IS NULL OR v_income_id IS NULL THEN
            RAISE EXCEPTION 'Required GL accounts 3010 and 4100 are missing for organization %', r.organization_id;
        END IF;

        INSERT INTO public.journal_entries (
            organization_id, branch_id, entry_date, source_type, source_id,
            reference, description, created_by, reversed, business_owner_only
        ) VALUES (
            r.organization_id, r.branch_id, v_entry_date,
            'APPLICATION_FEE_INCOME_RECLASSIFICATION', v_source_id,
            v_reference,
            'Reclassify collected legacy application fee to income — ' || v_reference,
            'SYSTEM-MIGRATION-V131', false, r.business_owner_only
        ) RETURNING id INTO v_journal_id;

        INSERT INTO public.journal_lines (journal_entry_id, account_id, debit, credit, description)
        VALUES
            (v_journal_id, v_equity_id, v_fee, 0,
             'Remove collected legacy application fee from migration equity — ' || v_reference),
            (v_journal_id, v_income_id, 0, v_fee,
             'Recognize collected legacy application fee income — ' || v_reference);
    END LOOP;
END $$;
