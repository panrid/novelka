-- Weekly look at the original: when the novel's site was last asked for its table of contents.
ALTER TABLE novel ADD COLUMN source_checked_at TIMESTAMPTZ;

-- The Studio's bell: who of the team hears that the original has chapters to translate.
CREATE TABLE source_watch
(
    account_id BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    edition_id BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, edition_id)
);

-- Telegram: new chapters of the original to translate.
ALTER TABLE telegram_link ADD COLUMN notify_source BOOLEAN NOT NULL DEFAULT TRUE;
