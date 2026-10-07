-- Telegram notifications: the chat tied to an account and what it wants to hear about.
CREATE TABLE telegram_link
(
    account_id      BIGINT PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    chat_id         BIGINT      NOT NULL UNIQUE,
    username        TEXT,
    notify_inbox    BOOLEAN     NOT NULL DEFAULT TRUE, -- replies, mentions, suggestions and the rest of «Сповіщення»
    notify_chapters BOOLEAN     NOT NULL DEFAULT TRUE, -- new chapters of subscribed translations
    notify_messages BOOLEAN     NOT NULL DEFAULT TRUE, -- personal and group messages
    linked_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The one-time code in t.me/<bot>?start=<code>. Only its SHA-256 is stored.
CREATE TABLE telegram_link_code
(
    code_hash  TEXT PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX telegram_link_code_by_account ON telegram_link_code (account_id);
