-- CRB Consumer mandatory identity/location fields.
-- Historical borrowers are allowed to remain nullable so an upgrade does not
-- destroy existing data. New borrower creation/export validation enforces them.

ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS place_of_birth VARCHAR(255);

ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS physical_address_province VARCHAR(255);

ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS physical_address_district VARCHAR(255);

ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS physical_address_sector VARCHAR(255);

ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS physical_address_cell VARCHAR(255);

ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS physical_address_village VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_borrower_crb_location
    ON borrowers (
        organization_id,
        physical_address_province,
        physical_address_district,
        physical_address_sector,
        physical_address_cell
    );
