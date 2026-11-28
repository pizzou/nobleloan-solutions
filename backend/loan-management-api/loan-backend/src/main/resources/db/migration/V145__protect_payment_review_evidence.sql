CREATE FUNCTION protect_payment_review_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'PENDING' THEN
            RAISE EXCEPTION 'Payment reviews must start pending' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payment review evidence cannot be deleted' USING ERRCODE = '23514';
    END IF;

    IF OLD.status <> 'PENDING' OR NEW.status NOT IN ('APPROVED', 'REJECTED')
        OR (to_jsonb(OLD) - ARRAY['status', 'decided_by', 'decided_at', 'decision_reason'])
           IS DISTINCT FROM
           (to_jsonb(NEW) - ARRAY['status', 'decided_by', 'decided_at', 'decision_reason'])
    THEN
        RAISE EXCEPTION 'Only the first decision can change a payment review' USING ERRCODE = '23514';
    END IF;

    IF NEW.status = 'REJECTED' AND NULLIF(BTRIM(NEW.decision_reason), '') IS NULL THEN
        RAISE EXCEPTION 'A rejected payment review requires a reason' USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_review_evidence
    BEFORE INSERT OR UPDATE OR DELETE ON payment_reviews
    FOR EACH ROW EXECUTE FUNCTION protect_payment_review_evidence();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_app') THEN
        REVOKE DELETE, TRUNCATE ON payment_reviews FROM loansaas_app;
    END IF;
END;
$$;
