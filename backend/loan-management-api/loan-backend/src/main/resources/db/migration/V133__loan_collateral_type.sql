-- Classify collateral per loan, never per borrower. Existing loans default to NONE.
ALTER TABLE public.loans ADD COLUMN collateral_type varchar(12) NOT NULL DEFAULT 'NONE';
ALTER TABLE public.loans ADD CONSTRAINT ck_loans_collateral_type
    CHECK (collateral_type IN ('NONE', 'MOVABLE', 'IMMOVABLE'));
