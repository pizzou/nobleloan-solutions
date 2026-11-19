-- Keep notification audit history while allowing recipients to clear their inbox.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS dismissed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_notifications_user_visible_created
    ON notifications (user_id, created_at DESC)
    WHERE dismissed_at IS NULL;
