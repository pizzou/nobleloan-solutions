-- V71__add_website_content_json.sql

ALTER TABLE organizations
ADD COLUMN IF NOT EXISTS website_content_json TEXT;