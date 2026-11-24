-- HISTORICAL FINANCIAL CORRECTION CANDIDATE — NLS-000054
-- IMPORTANT: This is NOT a Flyway migration and NEVER runs on application boot.
-- Uses a fixed verified Nov 24 baseline and is intentionally fail-closed.
-- Run on an ISOLATED RESTORED COPY first. Default: ROLLBACK.
-- Only independent accounting approval and current evidence can authorize APPLY=true.
-- PostgreSQL psql entry point: psql -X -v ON_ERROR_STOP=1 -v APPLY=false -f ...
-- Original receipt is not deleted or modified; its reversal flag is preserved
-- in the immutable ledger and replaced by a dated corrected allocation event.
-- NO SECOND CASH JOURNAL is posted; only a balanced reclassification journal.
--
-- Required evidence (to be independently validated on restored DB):
--   Loan 54; principal initially 33,500,000 throughout 2026-09-30..2026-11-19
--   Contract maturity 2026-09-26; 3 full grace days, 10% monthly.
--   Receipt ID 5 for 12,100,000 on 2026-11-19; installment ID 29.
--   Verified due contractual interest / management balances 6,300,000 each.
--   No additional receipts or journal accruals since Nov 24 baseline.
--   Corrected penalty as of 2026-11-19 = 5,583,333.33
--   Corrected penalty assessed through Nov 24 = 6,141,666.67
--   Historical opening penalty adjustment = -2,903,333.33 (account 3010)
--   Postreceipt penalty accrual adjustment = +175,000.00 (account 4100)
--   Preexisting total-repayable discrepancy is also reconciled from components.
--   Reclassification GL entries use the corrected original November reporting
--   dates ONLY while November accounting period is still OPEN.
--
-- NEVER execute APPLY=true against production without first comparing the
-- original source ledger and obtaining independent correction approval.

\set ON_ERROR_STOP on
-- A committed correction requires an independent approval reference.
-- Simulation never needs APPROVAL_REF and ALWAYS rolls back.
\if :{?APPLY}
\if :APPLY
\if :{?APPROVAL_REF}
\echo 'Approved correction reference supplied; applying guarded financial adjustments'
\else
\echo 'ERROR: APPLY=true requires APPROVAL_REF from independent review'
\quit
\endif
\endif
\endif
\if :{?APPROVAL_REF}
SELECT set_config('nls.correction_approval_ref', :'APPROVAL_REF', false);
\else
SELECT set_config('nls.correction_approval_ref', 'SIMULATION_ONLY', false);
\endif
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '90s';

DO $correction$
DECLARE
    l RECORD;
    t RECORD;
    installment RECORD;
    corrected_id BIGINT;
    journal_id BIGINT;
    n INTEGER;
    current_penalty_gl NUMERIC(19,2);
    sum_components NUMERIC(19,2);
    correction_ref TEXT := 'NLS54-MATURITY-20261124';
    approval_reference TEXT := current_setting('nls.correction_approval_ref');
    opening_offset NUMERIC(19,2) := 2903333.33;
    current_period_increment NUMERIC(19,2) := 175000.00;
    penalty_repaid NUMERIC(19,2) := 5583333.33;
    interest_repaid NUMERIC(19,2) := 3258333.33;
    management_repaid NUMERIC(19,2) := 3258333.34;
BEGIN
    IF length(approval_reference) < 8 THEN
        RAISE EXCEPTION 'Correction evidence/approval reference must contain at least 8 characters';
    END IF;
    SELECT * INTO STRICT l FROM loans
    WHERE id=54 AND reference_number='NLS-000054'
    FOR UPDATE;
    -- This correction is deliberately restricted to the OPEN November 2026
    -- accounting period. Any later application needs a newly approved
    -- current-period adjustment package rather than backdating.
    IF date_trunc('month', CURRENT_DATE) <> DATE '2026-11-01'
       OR EXISTS (
           SELECT 1 FROM accounting_periods
           WHERE organization_id=l.organization_id
             AND year=2026 AND month=11 AND status='CLOSED'
       ) THEN
        RAISE EXCEPTION 'November 2026 financial period is not currently open: abort';
    END IF;

    IF l.status <> 'OVERDUE' OR NOT l.imported
       OR l.disbursed_at::date <> DATE '2026-03-26'
       OR l.duration_months <> 6
       OR l.maturity_date <> DATE '2026-09-26'
       OR l.penalty_rate <> 10
       OR l.amount <> 33500000 OR l.outstanding_balance <> 24750000
       OR l.principal_paid <> 8750000
       OR l.total_paid <> 19600000
       OR l.interest_paid <> 5425000
       OR l.management_fee_paid <> 5425000
       OR l.interest_outstanding <> 4625000
       OR l.management_fee_outstanding <> 4625000
       OR l.penalties_assessed <> 8870000
       OR l.penalties_paid <> 0
       OR l.total_interest <> 10050000
       OR l.management_fee <> 10050000
       OR l.total_repayable <> 61305000 THEN
        RAISE EXCEPTION 'Loan 54 snapshot changed or opening historical evidence is inconsistent: abort';
    END IF;

    SELECT * INTO STRICT t
    FROM payment_transactions
    WHERE id=5 AND loan_id=54 FOR UPDATE;
    IF t.status <> 'POSTED' OR t.reversed
        OR t.installment_id IS DISTINCT FROM 29
        OR t.created_at::date <> DATE '2026-11-19'
        OR t.amount <> 12100000
        OR COALESCE(t.principal_component,0) <> 8750000
        OR COALESCE(t.interest_component,0) <> 1675000
        OR COALESCE(t.management_fee_component,0) <> 1675000
        OR COALESCE(t.penalty_component,0) <> 0
        OR COALESCE(t.extension_fee_component,0) <> 0
        OR COALESCE(t.unapplied_amount,0) <> 0 THEN
        RAISE EXCEPTION 'Original posted receipt changed or is already reversed: abort';
    END IF;

    SELECT * INTO STRICT installment FROM payments
    WHERE id=29 AND loan_id=54 FOR UPDATE;
    IF COALESCE(installment.amount_paid,0) <> 12100000
       OR COALESCE(installment.principal_component,0) <> 8750000
       OR COALESCE(installment.interest_component,0) <> 1675000
       OR COALESCE(installment.management_fee_component,0) <> 1675000
       OR COALESCE(installment.penalty_paid,0) <> 0 THEN
        RAISE EXCEPTION 'Installment cumulative components changed: abort';
    END IF;

    IF (SELECT COUNT(*) FROM payment_transactions
        WHERE loan_id=54 AND status='POSTED' AND NOT COALESCE(reversed,false)) <> 1
       OR EXISTS (SELECT 1 FROM journal_entries
           WHERE reference='NLS-000054'
           AND source_type='PENALTY_ACCRUAL'
           AND entry_date > DATE '2026-11-24')
       OR EXISTS (SELECT 1 FROM journal_entries
           WHERE organization_id=l.organization_id
           AND source_id LIKE 'LOAN:54:CORRECTION:%')
       OR (SELECT COUNT(*) FROM chart_of_accounts
           WHERE organization_id=l.organization_id
           AND code IN ('1100','1150','1160','1175','3010','4100')) <> 6 THEN
        RAISE EXCEPTION 'Additional activity, existing correction, or missing GL accounts: abort';
    END IF;

    SELECT ROUND(COALESCE(SUM(jl.debit-jl.credit),0),2)
      INTO current_penalty_gl
    FROM journal_entries je
    JOIN journal_lines jl ON jl.journal_entry_id=je.id
    JOIN chart_of_accounts a ON a.id=jl.account_id
    WHERE je.organization_id=l.organization_id
      AND je.reference=l.reference_number AND a.code='1175';
    IF current_penalty_gl <> 8870000 THEN
        RAISE EXCEPTION 'GL 1175 is %, expected 8870000: abort', current_penalty_gl;
    END IF;

    -- All receivable ledger opening positions must match the operational
    -- subledger BEFORE any adjusting entry can be posted.
    SELECT COUNT(*) INTO n FROM (
        SELECT a.code, ROUND(SUM(jl.debit-jl.credit),2) AS gl_balance
        FROM chart_of_accounts a
        JOIN journal_lines jl ON jl.account_id=a.id
        JOIN journal_entries je ON je.id=jl.journal_entry_id
        WHERE a.organization_id=l.organization_id
          AND je.reference=l.reference_number
          AND a.code IN ('1100','1150','1160')
        GROUP BY a.code
    ) balances
    WHERE (code='1100' AND gl_balance <> 24750000)
       OR (code='1150' AND gl_balance <> 4625000)
       OR (code='1160' AND gl_balance <> 4625000);
    IF n <> 0 THEN
        RAISE EXCEPTION 'Principal/interest/management GL baseline differs: abort';
    END IF;

    sum_components := penalty_repaid + interest_repaid + management_repaid;
    IF sum_components <> t.amount THEN
        RAISE EXCEPTION 'Corrected receipt allocation not cash balanced';
    END IF;

    -- Record original immutable receipt as reversed WITHOUT changing its
    -- historical amount, allocation components, source date or provider fields.
    UPDATE payment_transactions
       SET reversed=true, status='REVERSED', reversed_at=CURRENT_TIMESTAMP,
           reversal_reason='Audited allocation correction; NO cash refund; approval='||approval_reference,
           reversal_reference=correction_ref
     WHERE id=t.id;

    INSERT INTO payment_transactions (
        loan_id, organization_id, installment_id, recorded_by,
        transaction_reference, amount,
        penalty_component, interest_component, management_fee_component,
        principal_component, extension_fee_component, unapplied_amount,
        provider, currency, external_reference, gateway_status,
        payment_method, channel, notes, status, reversed, created_at)
    VALUES (
        t.loan_id, t.organization_id, t.installment_id, t.recorded_by,
        'CORRECTION-OF-PTX-'||t.id||'-'||correction_ref, t.amount,
        penalty_repaid, interest_repaid, management_repaid,
        0, 0, 0,
        t.provider, t.currency, t.external_reference, t.gateway_status,
        t.payment_method, t.channel,
        'Approved correction of PTX '||t.id||'; approval='||approval_reference,
        'POSTED', false, t.created_at)
    RETURNING id INTO corrected_id;

    -- This operation changes classification only, never cash bank balance.
    -- 1100 DR 8,750,000; 1150 CR 1,583,333.33;
    -- 1160 CR 1,583,333.34; 1175 CR 5,583,333.33.
    INSERT INTO journal_entries
        (organization_id,branch_id,entry_date,source_type,source_id,
         reference,description,created_by,reversed,business_owner_only)
    VALUES
        (l.organization_id,l.branch_id,DATE '2026-11-19',
         'PAYMENT_ALLOCATION_CORRECTION',
         'LOAN:54:CORRECTION:PAYMENT','NLS-000054',
         'No-cash correction of original receipt PTX 5; replacement PTX '
           ||corrected_id||'; original journal 306 retained; approval='
             ||approval_reference,'FINANCIAL_RECONCILIATION',
         false,COALESCE(l.business_owner_only,false))
    RETURNING id INTO journal_id;

    INSERT INTO journal_lines(journal_entry_id,account_id,debit,credit,description)
    SELECT journal_id, id,
           CASE code WHEN '1100' THEN 8750000 ELSE 0 END,
           CASE code WHEN '1150' THEN 1583333.33
                     WHEN '1160' THEN 1583333.34
                     WHEN '1175' THEN 5583333.33 ELSE 0 END,
           'Receipt 5 reclassification without a new cash movement'
    FROM chart_of_accounts WHERE organization_id=l.organization_id
        AND code IN ('1100','1150','1160','1175');

    -- Unwind overstated historical opening penalties via migration equity,
    -- not current-period income.
    INSERT INTO journal_entries
        (organization_id,branch_id,entry_date,source_type,source_id,
         reference,description,created_by,reversed,business_owner_only)
    VALUES
        (l.organization_id,l.branch_id,DATE '2026-11-13',
         'PENALTY_OPENING_REASSESSMENT',
         'LOAN:54:CORRECTION:OPENING','NLS-000054',
         'Reverse unsupported pre-maturity penalty; approval='
           ||approval_reference,
         'FINANCIAL_RECONCILIATION',false,COALESCE(l.business_owner_only,false))
    RETURNING id INTO journal_id;
    INSERT INTO journal_lines(journal_entry_id,account_id,debit,credit,description)
    SELECT journal_id,id,
           CASE WHEN code='3010' THEN opening_offset ELSE 0 END,
           CASE WHEN code='1175' THEN opening_offset ELSE 0 END,
           'Reclassified historical penalty opening after contract maturity evidence'
    FROM chart_of_accounts WHERE organization_id=l.organization_id
        AND code IN ('3010','1175');

    -- Recalculate accrual after principal restoration: extra 175,000
    -- is recognized as current-period penalty income.
    INSERT INTO journal_entries
        (organization_id,branch_id,entry_date,source_type,source_id,
         reference,description,created_by,reversed,business_owner_only)
    VALUES
        (l.organization_id,l.branch_id,DATE '2026-11-24',
         'PENALTY_CURRENT_REASSESSMENT',
         'LOAN:54:CORRECTION:POSTPAYMENT','NLS-000054',
         'Additional penalty due Nov 20-24; approval='
           ||approval_reference,
         'FINANCIAL_RECONCILIATION',false,COALESCE(l.business_owner_only,false))
    RETURNING id INTO journal_id;
    INSERT INTO journal_lines(journal_entry_id,account_id,debit,credit,description)
    SELECT journal_id,id,
           CASE WHEN code='1175' THEN current_period_increment ELSE 0 END,
           CASE WHEN code='4100' THEN current_period_increment ELSE 0 END,
           'Post-payment daily penalty corrected after principal restoration'
    FROM chart_of_accounts WHERE organization_id=l.organization_id
        AND code IN ('1175','4100');

    UPDATE loans
       SET principal_paid=0, outstanding_balance=33500000,
           interest_paid=7008333.33, interest_outstanding=3041666.67,
           management_fee_paid=7008333.34, management_fee_outstanding=3041666.66,
           penalties_assessed=6141666.67, penalties_paid=5583333.33,
           total_repayable=59741666.67,
           days_overdue=GREATEST(0,CURRENT_DATE-DATE '2026-09-26')
     WHERE id=54;

    UPDATE payments
       SET principal_component=0, interest_component=interest_repaid,
           management_fee_component=management_repaid,
           penalty_paid=penalty_repaid,
           penalty=6141666.67,
           outstanding_after=33500000,
           cycle_interest_due=6300000, cycle_interest_remaining=3041666.67,
           cycle_management_fee_due=6300000, cycle_management_fee_remaining=3041666.66
     WHERE id=29;

    IF EXISTS (SELECT 1 FROM loans l2 WHERE id=54 AND
        (l2.total_paid <> 19600000 OR
         ROUND(l2.amount-l2.principal_paid-l2.outstanding_balance,2)<>0 OR
         ROUND(l2.total_interest-l2.interest_paid-l2.interest_outstanding,2)<>0 OR
         ROUND(l2.management_fee-l2.management_fee_paid-l2.management_fee_outstanding,2)<>0 OR
         ROUND(l2.total_repayable-l2.total_paid-l2.outstanding_balance-
             l2.interest_outstanding-l2.management_fee_outstanding-
             (l2.penalties_assessed-l2.penalties_paid)-
             l2.extension_fee_outstanding,2)<>0)) THEN
        RAISE EXCEPTION 'Post-reclassification operational reconciliation failed';
    END IF;

    SELECT ROUND(COALESCE(SUM(jl.debit-jl.credit),0),2)
      INTO current_penalty_gl
    FROM journal_entries je
    JOIN journal_lines jl ON jl.journal_entry_id=je.id
    JOIN chart_of_accounts a ON a.id=jl.account_id
    WHERE je.organization_id=l.organization_id
      AND je.reference=l.reference_number AND a.code='1175';
    IF current_penalty_gl <> 558333.34 THEN
        RAISE EXCEPTION 'Corrected loan 54 penalty GL balance % is not 558333.34',
            current_penalty_gl;
    END IF;
    SELECT COUNT(*) INTO n FROM (
        SELECT a.code, ROUND(SUM(jl.debit-jl.credit),2) AS gl_balance
        FROM chart_of_accounts a
        JOIN journal_lines jl ON jl.account_id=a.id
        JOIN journal_entries je ON je.id=jl.journal_entry_id
        WHERE a.organization_id=l.organization_id
          AND je.reference=l.reference_number
          AND a.code IN ('1100','1150','1160')
        GROUP BY a.code
    ) balances
    WHERE (code='1100' AND gl_balance <> 33500000)
       OR (code='1150' AND gl_balance <> 3041666.67)
       OR (code='1160' AND gl_balance <> 3041666.66);
    IF n <> 0 THEN
        RAISE EXCEPTION 'Corrected loan GL balances do not reconcile: abort';
    END IF;
    IF (SELECT COUNT(*) FROM payment_transactions
        WHERE loan_id=54 AND status='POSTED' AND NOT COALESCE(reversed,false)) <> 1 THEN
        RAISE EXCEPTION 'Corrected immutable receipt ledger has wrong number of posted receipts';
    END IF;
    RAISE NOTICE 'Verified reclassification candidate. Replacement receipt ID %, cash unchanged %; penalty receivable %. This transaction rolls back unless APPLY=true.',
        corrected_id, t.amount, current_penalty_gl;
END $correction$;

-- Simulated by default. The script may only be committed after independent
-- review, successful restored-database test and authorized written evidence.
\if :{?APPLY}
\if :APPLY
COMMIT;
\else
ROLLBACK;
\endif
\else
ROLLBACK;
\endif
