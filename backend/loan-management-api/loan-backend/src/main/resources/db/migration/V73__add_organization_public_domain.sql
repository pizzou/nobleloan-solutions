ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS public_domain VARCHAR(255);

/*
 * Backfill the canonical public hostname from the existing website URL
 * where possible. This keeps existing tenants reachable without requiring
 * a manual data rewrite after deployment.
 */
UPDATE organizations
SET public_domain = lower(
        regexp_replace(
            regexp_replace(
                trim(website),
                '^https?://',
                ''
            ),
            '/.*$',
            ''
        )
    )
WHERE (public_domain IS NULL OR trim(public_domain) = '')
  AND website IS NOT NULL
  AND trim(website) <> '';

UPDATE organizations
SET public_domain = NULL
WHERE public_domain IS NOT NULL
  AND trim(public_domain) = '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_organizations_public_domain
    ON organizations(public_domain)
    WHERE public_domain IS NOT NULL AND trim(public_domain) <> '';
