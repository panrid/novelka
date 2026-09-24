-- Replies point at a message of the same thread. Mentions are stored in the text as account-id tokens,
-- so a nickname change never breaks them.
ALTER TABLE comments ADD COLUMN reply_to bigint REFERENCES comments(id);
ALTER TABLE chat_messages ADD COLUMN reply_to bigint REFERENCES chat_messages(id);
-- Personal notifications about mentions and replies.
ALTER TABLE notifications DROP CONSTRAINT notifications_kind_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_kind_check CHECK (kind IN
    ('chapter_published','glossary_added','task_complete','task_failed','task_interrupted','mention','reply'));
ALTER TABLE notifications DROP CONSTRAINT notifications_audience_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_audience_check CHECK (audience IN ('READER','ADMIN','PERSONAL'));
ALTER TABLE notifications ADD COLUMN actor_id text REFERENCES accounts(id);
ALTER TABLE notifications ADD COLUMN comment_id bigint REFERENCES comments(id);
ALTER TABLE notifications ADD COLUMN chat_id bigint REFERENCES chat_messages(id)
