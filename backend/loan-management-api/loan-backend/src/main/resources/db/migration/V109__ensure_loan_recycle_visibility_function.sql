CREATE OR REPLACE FUNCTION public.loan_is_visible(p_loan_id bigint)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM loans l
        WHERE l.id = p_loan_id
          AND l.deleted_at IS NULL
    );
$$;