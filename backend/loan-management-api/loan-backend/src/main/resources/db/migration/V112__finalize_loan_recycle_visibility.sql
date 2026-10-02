-- V111: finalize Hibernate-safe loan recycle-bin visibility.
-- The Loan entity uses the database function below instead of a raw
-- deleted_at predicate so Hibernate cannot incorrectly reuse the outer
-- Payment alias when loading Payment -> Loan associations.

CREATE OR REPLACE FUNCTION public.loan_is_visible(p_loan_id BIGINT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.loans l
        WHERE l.id = p_loan_id
          AND l.deleted_at IS NULL
    );
$$;

COMMENT ON FUNCTION public.loan_is_visible(BIGINT) IS
'Returns true when a referenced loan remains operationally visible. Recycled loans remain stored during the recovery window but are excluded from normal entity queries.';
