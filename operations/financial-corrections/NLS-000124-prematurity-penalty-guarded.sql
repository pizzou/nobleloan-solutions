-- NLS-000124 PREMATURITY PENALTY CORRECTION (NOT A FLYWAY MIGRATION)
-- Financial adjustment only; never modify existing payment or journal lines.
-- Use ONLY on restored test DB first. Default ROLLBACK.
-- Source evidence: loan 124 maturity 2027-01-12; nine incorrect daily
-- penalty accrual journals Nov 16–24, 2026, totaling RWF 60,000.
-- Correcting entry: DR 4100 penalty income RWF 60,000 / CR 1175 penalty
-- receivable RWF 60,000. No bank/cash entry. Original journal history kept.
-- After independent approval, the adjusted loan has zero assessed penalty,
-- no penalty due, zero overdue days and ACTIVE status.
-- This script is deliberately snapshot/period guarded and idempotent-failing.
\set ON_ERROR_STOP on
\if :{?APPLY}
\if :APPLY
\if :{?APPROVAL_REF}
\else
\echo 'ERROR: APPLY=true requires an independent APPROVAL_REF'
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
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';

DO $$
DECLARE
  l RECORD;
  a RECORD;
  n INTEGER;
  accrual_count INTEGER;
  accrual_amount NUMERIC(19,2);
  gl_due NUMERIC(19,2);
  journal_id BIGINT;
  approval TEXT := current_setting('nls.correction_approval_ref');
BEGIN
  SELECT * INTO STRICT l FROM loans
   WHERE id=124 AND reference_number='NLS-000124' FOR UPDATE;

  IF CURRENT_DATE <> DATE '2026-11-25'
     OR l.status <> 'OVERDUE'
     OR l.imported IS DISTINCT FROM true
     OR l.disbursed_at::date <> DATE '2026-10-12'
     OR l.duration_months <> 3
     OR (l.disbursed_at::date + make_interval(months=>l.duration_months))::date
          <> DATE '2027-01-12'
     OR l.amount <> 2000000
     OR l.outstanding_balance <> 2000000
     OR l.principal_paid <> 0
     OR l.penalties_assessed <> 60000
     OR l.penalties_paid <> 0
     OR l.interest_outstanding <> 200000
     OR l.management_fee_outstanding <> 200000
     OR l.interest_paid <> 100000
     OR l.management_fee_paid <> 100000 THEN
    RAISE EXCEPTION 'Loan 124 snapshot or current date differs; abort';
  END IF;

  IF EXISTS (SELECT 1 FROM accounting_periods p
      WHERE p.organization_id=l.organization_id AND p.year=2026
        AND p.month=11 AND p.status='CLOSED') THEN
    RAISE EXCEPTION 'November accounting period is closed; abort';
  END IF;

  IF EXISTS (SELECT 1 FROM journal_entries je
      WHERE je.organization_id=l.organization_id
        AND je.source_type='PREMATURITY_PENALTY_RECLASSIFICATION'
        AND je.source_id='LOAN:124:CORRECTION:PREMATURITY:20261125')
     OR EXISTS (SELECT 1 FROM journal_entries je
      WHERE je.reference=l.reference_number
        AND je.source_type='PENALTY_ACCRUAL'
        AND je.entry_date > DATE '2026-11-24') THEN
    RAISE EXCEPTION 'Correction already exists or later penalty accrual posted; abort';
  END IF;

  SELECT COUNT(*), ROUND(COALESCE(SUM(jl.debit-jl.credit),0),2)
    INTO accrual_count, accrual_amount
    FROM journal_entries je
    JOIN journal_lines jl ON jl.journal_entry_id=je.id
    JOIN chart_of_accounts a ON a.id=jl.account_id
   WHERE je.organization_id=l.organization_id
     AND je.reference=l.reference_number
     AND je.source_type='PENALTY_ACCRUAL'
     AND NOT COALESCE(je.reversed,false)
     AND je.entry_date BETWEEN DATE '2026-11-16' AND DATE '2026-11-24'
     AND a.code='1175';
  IF accrual_count<>9 OR accrual_amount<>60000 THEN
    RAISE EXCEPTION 'Expected nine misdated accruals totaling RWF 60000; got % journals / RWF %',
      accrual_count,accrual_amount;
  END IF;

  SELECT ROUND(COALESCE(SUM(jl.debit-jl.credit),0),2)
    INTO gl_due
    FROM journal_entries je
    JOIN journal_lines jl ON jl.journal_entry_id=je.id
    JOIN chart_of_accounts a ON a.id=jl.account_id
   WHERE je.organization_id=l.organization_id AND a.code='1175'
     AND NOT COALESCE(je.reversed,false)
     AND (je.reference=l.reference_number
       OR je.source_id='LOAN:'||l.id
       OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
           AND EXISTS (SELECT 1 FROM payments p
             WHERE p.id::text=je.source_id AND p.loan_id=l.id)));
  IF gl_due<>60000 THEN
    RAISE EXCEPTION 'Penalty GL 1175 should be RWF 60000 but is %',gl_due;
  END IF;

  SELECT COUNT(*) INTO n FROM chart_of_accounts
    WHERE organization_id=l.organization_id AND code IN ('1175','4100');
  IF n<>2 THEN
    RAISE EXCEPTION 'Required accounting accounts 1175/4100 missing or duplicated';
  END IF;

  INSERT INTO journal_entries
      (organization_id,branch_id,entry_date,source_type,source_id,
       reference,description,created_by,reversed,business_owner_only)
  VALUES
      (l.organization_id,l.branch_id,DATE '2026-11-25',
       'PREMATURITY_PENALTY_RECLASSIFICATION',
       'LOAN:124:CORRECTION:PREMATURITY:20261125',
       l.reference_number,
       'Reverse unsupported Nov 16-24 early penalty, original accrual journals retained; approval='||approval,
       'FINANCIAL_RECONCILIATION',false,COALESCE(l.business_owner_only,false))
  RETURNING id INTO journal_id;

  INSERT INTO journal_lines
      (journal_entry_id,account_id,debit,credit,description)
  SELECT journal_id,a.id,
       CASE WHEN a.code='4100' THEN 60000 ELSE 0 END,
       CASE WHEN a.code='1175' THEN 60000 ELSE 0 END,
       'Early penalty reversal, no cash movement; original journals retained'
  FROM chart_of_accounts a
  WHERE a.organization_id=l.organization_id
    AND a.code IN ('1175','4100');

  UPDATE loans SET
       penalties_assessed=0,
       total_repayable=total_repayable-60000,
       status='ACTIVE',
       days_overdue=0,
       arrears_status='NOT_DUE'
   WHERE id=124;

  SELECT ROUND(COALESCE(SUM(jl.debit-jl.credit),0),2)
    INTO gl_due
    FROM journal_entries je
    JOIN journal_lines jl ON jl.journal_entry_id=je.id
    JOIN chart_of_accounts a ON a.id=jl.account_id
   WHERE je.organization_id=l.organization_id AND a.code='1175'
     AND NOT COALESCE(je.reversed,false)
     AND (je.reference=l.reference_number
       OR je.source_id='LOAN:'||l.id
       OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
           AND EXISTS (SELECT 1 FROM payments p
             WHERE p.id::text=je.source_id AND p.loan_id=l.id)));
  IF gl_due<>0 OR EXISTS(
      SELECT 1 FROM loans
      WHERE id=124 AND (penalties_assessed<>0 OR penalties_paid<>0
         OR days_overdue<>0 OR status<>'ACTIVE'
         OR outstanding_balance<>2000000)) THEN
    RAISE EXCEPTION 'Postcorrection subledger/GL maturity reconciliation failed';
  END IF;
  RAISE NOTICE 'NLS-000124 corrected on isolated data: penalty GL %, 9 original accrual journals retained, no cash movement. Rollback unless APPLY=true',gl_due;
END $$;

\if :{?APPLY}
\if :APPLY
COMMIT;
\else
ROLLBACK;
\endif
\else
ROLLBACK;
\endif
