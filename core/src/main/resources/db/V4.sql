CREATE TABLE notifications (
    id bigserial PRIMARY KEY,
    event_key text NOT NULL UNIQUE,
    kind text NOT NULL CHECK (kind IN ('chapter_published','glossary_added','task_complete','task_failed','task_interrupted')),
    audience text NOT NULL CHECK (audience IN ('READER','ADMIN')),
    novel_id text,
    chapter integer,
    task_id text,
    entry_count integer,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_audience_id ON notifications(audience,id DESC);
CREATE TABLE notification_reads (
    account_id text NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    notification_id bigint NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    read_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(account_id,notification_id)
);
