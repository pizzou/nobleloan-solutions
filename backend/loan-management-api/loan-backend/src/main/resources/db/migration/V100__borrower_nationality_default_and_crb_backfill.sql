-- CRB-safe borrower nationality normalization.
-- For Rwanda-based lending, missing/blank nationality is recorded as Rwandan.

UPDATE borrowers
SET nationality = 'Rwandan'
WHERE nationality IS NULL OR btrim(nationality) = '';

ALTER TABLE borrowers
    ALTER COLUMN nationality SET DEFAULT 'Rwandan';

ALTER TABLE borrowers
    ALTER COLUMN nationality SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_borrower_org_nationality
    ON borrowers (organization_id, nationality);
