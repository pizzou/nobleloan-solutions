-- ============================================================
-- V70: PRODUCTION ORGANIZATION PUBLIC SLUG
-- ============================================================

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS slug VARCHAR(120);

UPDATE organizations
SET slug = lower(
        regexp_replace(
                trim(name),
                '[^a-zA-Z0-9]+',
                '-',
                'g'
        )
    )
WHERE slug IS NULL
   OR trim(slug) = '';

UPDATE organizations
SET slug = regexp_replace(
        regexp_replace(slug, '^-+', ''),
        '-+$',
        ''
    )
WHERE slug IS NOT NULL;

UPDATE organizations
SET slug = CASE
               WHEN slug IS NULL
                    OR trim(slug) = ''
                   THEN 'organization-' || id
               ELSE slug
           END;

WITH duplicates AS (
    SELECT
        id,
        slug,
        ROW_NUMBER() OVER (
            PARTITION BY slug
            ORDER BY id
        ) AS rn
    FROM organizations
)
UPDATE organizations o
SET slug = CASE
               WHEN d.rn = 1
                   THEN d.slug
               ELSE d.slug || '-' || o.id
           END
FROM duplicates d
WHERE o.id = d.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_organizations_slug
    ON organizations(slug);

ALTER TABLE organizations
    ALTER COLUMN slug SET NOT NULL;