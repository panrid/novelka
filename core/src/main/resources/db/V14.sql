-- Community chat. Clients poll for ids after the last one they have. Deletion is soft for moderation audit.
CREATE TABLE chat_messages (
    id bigserial PRIMARY KEY,
    author_id text NOT NULL REFERENCES accounts(id),
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    deleted_by text REFERENCES accounts(id)
);
CREATE INDEX chat_messages_deleted ON chat_messages(deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX chat_messages_author_created ON chat_messages(author_id, created_at DESC)
