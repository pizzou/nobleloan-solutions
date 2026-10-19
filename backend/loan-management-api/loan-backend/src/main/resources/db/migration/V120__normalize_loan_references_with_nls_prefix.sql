-- V120: make every loan reference use the canonical NLS- prefix.
--
-- This is a forward-only data migration. Amounts, dates, statuses, accounting
-- balances, posted journal rows/descriptions, and payment references are not
-- changed. Posted journal entries retain the loan reference recorded at posting
-- time; operational loan/tombstone/work-queue references are normalized below.

CREATE TEMPORARY TABLE nls_loan_reference_map (
    loan_id BIGINT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    old_reference TEXT NOT NULL,
    new_reference VARCHAR(100) NOT NULL,
    temp_reference VARCHAR(100)
) ON COMMIT DROP;

INSERT INTO nls_loan_reference_map (loan_id, organization_id, old_reference, new_reference)
SELECT l.id,
       l.organization_id,
       COALESCE(l.reference_number, ''),
       LEFT(
           'NLS-' || COALESCE(
               NULLIF(
                   BTRIM(REGEXP_REPLACE(
                       COALESCE(NULLIF(BTRIM(l.reference_number), ''), 'LEGACY-' || l.id::text),
                       '^NLS[-_[:space:]]*', '', 'i'
                   )),
                   ''
               ),
               'LEGACY-' || l.id::text
           ),
           100
       )
FROM public.loans l;

-- A legacy reference like "123" can collide with an already-existing
-- "NLS-123". Resolve every duplicate target deterministically by loan ID and
-- hash of the original reference, repeating if it collides with another target.
DO $$
DECLARE
    v_has_duplicates BOOLEAN;
    v_attempt INTEGER := 0;
BEGIN
    LOOP
        SELECT EXISTS (
            SELECT 1
            FROM nls_loan_reference_map
            GROUP BY LOWER(BTRIM(new_reference))
            HAVING COUNT(*) > 1
        ) INTO v_has_duplicates;

        EXIT WHEN NOT v_has_duplicates;

        UPDATE nls_loan_reference_map m
        SET new_reference = LEFT(
            'NLS-LEGACY-' || m.loan_id::text || '-' ||
            SUBSTRING(MD5(m.loan_id::text || ':' || m.old_reference || ':' || v_attempt::text) FROM 1 FOR 20),
            100
        )
        WHERE LOWER(BTRIM(m.new_reference)) IN (
            SELECT LOWER(BTRIM(dupe.new_reference))
            FROM nls_loan_reference_map dupe
            GROUP BY LOWER(BTRIM(dupe.new_reference))
            HAVING COUNT(*) > 1
        );

        v_attempt := v_attempt + 1;
        IF v_attempt > 20 THEN
            RAISE EXCEPTION 'Unable to produce unique NLS loan references after 20 collision-resolution attempts';
        END IF;
    END LOOP;
END $$;

-- Move only rows whose reference must change to unique temporary values before
-- applying final values. This prevents the loans.reference_number UNIQUE index
-- from blocking legitimate swaps/normalization during the migration.
DO $$
DECLARE
    r RECORD;
    v_temp TEXT;
    v_attempt INTEGER;
BEGIN
    FOR r IN
        SELECT loan_id
        FROM nls_loan_reference_map
        WHERE old_reference IS DISTINCT FROM new_reference
        ORDER BY loan_id
    LOOP
        v_attempt := 0;
        LOOP
            v_temp := '__NLS_MIGRATION_TEMP__' || r.loan_id::text || '_' ||
                      MD5(CLOCK_TIMESTAMP()::text || RANDOM()::text || r.loan_id::text || v_attempt::text);

            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM public.loans l
                WHERE LOWER(BTRIM(l.reference_number)) = LOWER(v_temp)
            )
            AND NOT EXISTS (
                SELECT 1 FROM nls_loan_reference_map m
                WHERE LOWER(BTRIM(m.new_reference)) = LOWER(v_temp)
            )
            AND NOT EXISTS (
                SELECT 1 FROM nls_loan_reference_map m
                WHERE m.temp_reference IS NOT NULL
                  AND LOWER(BTRIM(m.temp_reference)) = LOWER(v_temp)
            );

            v_attempt := v_attempt + 1;
            IF v_attempt > 100 THEN
                RAISE EXCEPTION 'Unable to allocate temporary reference for loan ID %', r.loan_id;
            END IF;
        END LOOP;

        UPDATE nls_loan_reference_map
        SET temp_reference = v_temp
        WHERE loan_id = r.loan_id;
    END LOOP;
END $$;

UPDATE public.loans l
SET reference_number = m.temp_reference
FROM nls_loan_reference_map m
WHERE l.id = m.loan_id
  AND m.old_reference IS DISTINCT FROM m.new_reference;

UPDATE public.loans l
SET reference_number = m.new_reference
FROM nls_loan_reference_map m
WHERE l.id = m.loan_id
  AND m.old_reference IS DISTINCT FROM m.new_reference;

-- Keep recycle-bin tombstones in sync for extant loans. Tombstones whose loan
-- was already permanently purged retain a canonical NLS- reference as well.
UPDATE public.loan_deletion_tombstones t
SET reference_number = COALESCE(
    (
        SELECT m.new_reference
        FROM nls_loan_reference_map m
        WHERE m.loan_id = t.loan_id
          AND m.organization_id = t.organization_id
    ),
    LEFT(
        'NLS-' || COALESCE(
            NULLIF(BTRIM(REGEXP_REPLACE(
                COALESCE(NULLIF(BTRIM(t.reference_number), ''), 'LEGACY-' || t.loan_id::text),
                '^NLS[-_[:space:]]*', '', 'i'
            )), ''),
            'LEGACY-' || t.loan_id::text
        ),
        255
    )
);

-- Work queue tasks carry a denormalized loan reference for display. Refresh it
-- from the authoritative loan/tombstone data, or normalize its stored value if
-- its loan has already been purged.
UPDATE public.workflow_tasks wt
SET reference_number = COALESCE(
    (
        SELECT m.new_reference
        FROM nls_loan_reference_map m
        WHERE m.loan_id = wt.entity_id
          AND m.organization_id = wt.organization_id
    ),
    (
        SELECT t.reference_number
        FROM public.loan_deletion_tombstones t
        WHERE t.loan_id = wt.entity_id
          AND t.organization_id = wt.organization_id
        ORDER BY t.deleted_at DESC
        LIMIT 1
    ),
    LEFT(
        'NLS-' || COALESCE(
            NULLIF(BTRIM(REGEXP_REPLACE(
                COALESCE(NULLIF(BTRIM(wt.reference_number), ''), 'LEGACY-' || wt.entity_id::text),
                '^NLS[-_[:space:]]*', '', 'i'
            )), ''),
            'LEGACY-' || wt.entity_id::text
        ),
        255
    )
)
WHERE UPPER(COALESCE(wt.entity_type, '')) = 'LOAN'
  AND wt.entity_id IS NOT NULL;

-- Database-level guard against future direct-SQL writes bypassing JPA.
ALTER TABLE public.loans
    ADD CONSTRAINT ck_loans_reference_number_nls_prefix
    CHECK (reference_number LIKE 'NLS-%' AND LENGTH(reference_number) > 4);

-- Stop deployment if any reference does not meet the enforced format.
DO $$
DECLARE
    v_bad_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_bad_count
    FROM public.loans
    WHERE reference_number IS NULL
       OR reference_number NOT LIKE 'NLS-%'
       OR LENGTH(reference_number) <= 4;

    IF v_bad_count > 0 THEN
        RAISE EXCEPTION 'V120 failed validation: % loan references do not start with NLS- and a non-empty suffix', v_bad_count;
    END IF;
END $$;
