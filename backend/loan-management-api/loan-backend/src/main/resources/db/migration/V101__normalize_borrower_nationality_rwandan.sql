-- Ensure every borrower has a regulatory-safe nationality value.
-- Rwanda-based lending defaults an omitted nationality to Rwandan; explicit
-- foreign nationalities remain unchanged.

UPDATE borrowers
SET nationality = 'Rwandan'
WHERE nationality IS NULL OR btrim(nationality) = '';

ALTER TABLE borrowers
    ALTER COLUMN nationality SET DEFAULT 'Rwandan';

ALTER TABLE borrowers
    ALTER COLUMN nationality SET NOT NULL;
