-- V107: safe recycle-bin visibility predicate for loan child entities.
-- Hibernate @SQLRestriction cannot safely use the previous correlated
-- subquery form because Hibernate aliases the subquery columns to the
-- outer entity table. This function keeps the visibility check explicit,
-- preserves the existing financial schema, and works for all direct
-- loan-child entities without adding deleted_at columns to payment or
-- other financial tables.

CREATE OR REPLACE FUNCTION public.loan_is_visible(
    p_loan_id BIGINT
)
RETURNS BOOLEAN
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

COMMENT ON FUNCTION public.loan_is_visible(BIGINT) IS
'Returns true when the referenced loan remains operationally visible. Recycled loans remain stored during the 30-day recovery window but are excluded from normal loan-child queries.';
