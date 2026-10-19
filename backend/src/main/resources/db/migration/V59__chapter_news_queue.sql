-- План 21.9: news of new chapters goes out through a queue, not inside the publish transaction.
-- Publishing writes one row here; a worker walks the subscribers 500 at a time by account id
-- (after_account_id is the keyset cursor) and writes their inbox rows batch by batch.
CREATE TABLE chapter_news
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id       BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    first_number     INT         NOT NULL,
    last_number      INT         NOT NULL CHECK (last_number >= first_number),
    after_account_id BIGINT      NOT NULL DEFAULT 0,
    state            TEXT        NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'done', 'cancelled', 'failed')),
    attempts         INT         NOT NULL DEFAULT 0,
    not_before       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);

CREATE INDEX chapter_news_ready ON chapter_news (not_before, id) WHERE state = 'pending';
CREATE INDEX chapter_news_finished ON chapter_news (finished_at) WHERE state <> 'pending';

-- Who has heard about one publication so far: a batch run again tells nobody twice. Cleared
-- when the publication is done.
CREATE TABLE chapter_news_recipient
(
    news_id    BIGINT NOT NULL REFERENCES chapter_news (id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    PRIMARY KEY (news_id, account_id)
);

-- Telegram messages about new chapters waiting to go: sent at Telegram's pace, retried with
-- pauses, dropped when the chapters were unpublished meanwhile. One per person per publication.
CREATE TABLE telegram_outbox
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    news_id      BIGINT      NOT NULL REFERENCES chapter_news (id) ON DELETE CASCADE,
    edition_id   BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    first_number INT         NOT NULL,
    last_number  INT         NOT NULL,
    kind         TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    -- sending: taken by the sender; a row left so by a crash is not sent again (it may have gone)
    state        TEXT        NOT NULL DEFAULT 'pending'
        CHECK (state IN ('pending', 'sending', 'sent', 'gone', 'cancelled', 'failed', 'unknown')),
    attempts     INT         NOT NULL DEFAULT 0,
    not_before   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ,
    UNIQUE (news_id, account_id)
);

CREATE INDEX telegram_outbox_ready ON telegram_outbox (not_before, id) WHERE state = 'pending';
CREATE INDEX telegram_outbox_sending ON telegram_outbox (not_before) WHERE state = 'sending';
CREATE INDEX telegram_outbox_finished ON telegram_outbox (finished_at) WHERE state NOT IN ('pending', 'sending');
