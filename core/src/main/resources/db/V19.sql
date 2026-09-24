-- Moderation hides instead of deleting: readers can still reveal a hidden message for themselves.
-- moderated_at lets chat clients pick up hide and restore changes of messages they already show.
ALTER TABLE comments ADD COLUMN hidden_at timestamptz;
ALTER TABLE comments ADD COLUMN hidden_by text REFERENCES accounts(id);
ALTER TABLE comments ADD COLUMN hidden_reason text NOT NULL DEFAULT '';
ALTER TABLE chat_messages ADD COLUMN hidden_at timestamptz;
ALTER TABLE chat_messages ADD COLUMN hidden_by text REFERENCES accounts(id);
ALTER TABLE chat_messages ADD COLUMN hidden_reason text NOT NULL DEFAULT '';
ALTER TABLE chat_messages ADD COLUMN moderated_at timestamptz;
CREATE INDEX chat_messages_moderated ON chat_messages(moderated_at) WHERE moderated_at IS NOT NULL;
-- A hidden novel disappears from the catalog. Only its translator and administrators can open it.
ALTER TABLE novels ADD COLUMN hidden_at timestamptz;
ALTER TABLE novels ADD COLUMN hidden_by text REFERENCES accounts(id);
ALTER TABLE novels ADD COLUMN hidden_reason text NOT NULL DEFAULT ''
