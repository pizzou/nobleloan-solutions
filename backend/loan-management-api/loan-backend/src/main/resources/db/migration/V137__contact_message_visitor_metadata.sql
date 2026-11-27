-- Contact metadata belongs only to messages sent through the public form.
-- Existing saved messages remain intact; new fields are nullable.
ALTER TABLE public.contact_messages
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45),
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS operating_system VARCHAR(60),
    ADD COLUMN IF NOT EXISTS browser_name VARCHAR(60),
    ADD COLUMN IF NOT EXISTS time_zone VARCHAR(100),
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512),
    ADD COLUMN IF NOT EXISTS approximate_location VARCHAR(120),
    ADD COLUMN IF NOT EXISTS location_consent BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN public.contact_messages.ip_address IS
    'Visitor IP as seen by the trusted reverse proxy; potentially personal data.';
COMMENT ON COLUMN public.contact_messages.approximate_location IS
    'Rounded coordinates supplied voluntarily after browser geolocation permission; may be unverified.';
