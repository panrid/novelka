-- Notifications the person put away: out of the list and the count, back with one press.
ALTER TABLE notification ADD COLUMN archived_at TIMESTAMPTZ;
