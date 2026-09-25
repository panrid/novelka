-- Community: comments on a translation or its chapter, the site chat, votes, ratings and
-- reports. Mentions live in the text as <@u:id> and <$t:id>, so renamed people and teams
-- stay mentioned.

CREATE TABLE comment
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id     BIGINT      NOT NULL REFERENCES edition (id),
    chapter_number INT,                               -- NULL: about the translation as a whole
    author_id      BIGINT      NOT NULL REFERENCES account (id),
    reply_to       BIGINT REFERENCES comment (id),    -- always a top-level comment
    body           TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
    score          INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at      TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    hidden_at      TIMESTAMPTZ,
    hidden_by      BIGINT REFERENCES account (id),
    hidden_reason  TEXT
);

CREATE INDEX comment_by_place ON comment (edition_id, chapter_number, created_at DESC);
CREATE INDEX comment_replies ON comment (reply_to, created_at) WHERE reply_to IS NOT NULL;

CREATE TABLE comment_vote
(
    comment_id BIGINT   NOT NULL REFERENCES comment (id),
    account_id BIGINT   NOT NULL REFERENCES account (id),
    value      SMALLINT NOT NULL CHECK (value IN (-1, 1)),
    PRIMARY KEY (comment_id, account_id)
);

CREATE TABLE chat_message
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id  BIGINT      NOT NULL REFERENCES account (id),
    reply_to   BIGINT REFERENCES chat_message (id),
    body       TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    hidden_at  TIMESTAMPTZ,
    hidden_by  BIGINT REFERENCES account (id)
);

CREATE TABLE edition_rating
(
    edition_id BIGINT      NOT NULL REFERENCES edition (id),
    account_id BIGINT      NOT NULL REFERENCES account (id),
    score      SMALLINT    NOT NULL CHECK (score BETWEEN 1 AND 5),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (edition_id, account_id)
);

CREATE TABLE report
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id BIGINT      NOT NULL REFERENCES account (id),
    target      TEXT        NOT NULL CHECK (target IN ('comment', 'chat', 'message', 'image')),
    target_id   BIGINT      NOT NULL,
    reason      TEXT        NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 500),
    state       TEXT        NOT NULL DEFAULT 'open' CHECK (state IN ('open', 'resolved', 'dismissed')),
    resolved_by BIGINT REFERENCES account (id),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reporter_id, target, target_id)
);

CREATE INDEX report_open ON report (created_at) WHERE state = 'open';

-- Messaging: direct conversations, groups and team chats.

CREATE TABLE account_block
(
    blocker_id BIGINT      NOT NULL REFERENCES account (id),
    blocked_id BIGINT      NOT NULL REFERENCES account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE TABLE conversation
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind            TEXT        NOT NULL CHECK (kind IN ('direct', 'group', 'team')),
    title           TEXT CHECK (char_length(title) <= 60),
    avatar_image_id BIGINT REFERENCES image (id),
    direct_key      TEXT UNIQUE,                       -- "smaller:larger" account ids
    team_id         BIGINT UNIQUE REFERENCES team (id),
    created_by      BIGINT REFERENCES account (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ
);

CREATE TABLE conversation_member
(
    conversation_id      BIGINT      NOT NULL REFERENCES conversation (id),
    account_id           BIGINT      NOT NULL REFERENCES account (id),
    role                 TEXT        NOT NULL DEFAULT 'member' CHECK (role IN ('admin', 'member')),
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    added_by             BIGINT REFERENCES account (id),
    left_at              TIMESTAMPTZ,
    last_read_message_id BIGINT,
    muted                BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (conversation_id, account_id)
);

CREATE INDEX conversation_member_by_account ON conversation_member (account_id) WHERE left_at IS NULL;

CREATE TABLE message
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversation (id),
    author_id       BIGINT REFERENCES account (id),   -- NULL for system lines
    kind            TEXT        NOT NULL DEFAULT 'text' CHECK (kind IN ('text', 'system')),
    reply_to        BIGINT REFERENCES message (id),
    body            TEXT        NOT NULL CHECK (char_length(body) <= 4000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX message_by_conversation ON message (conversation_id, id DESC);

CREATE TABLE message_image
(
    message_id BIGINT   NOT NULL REFERENCES message (id),
    image_id   BIGINT   NOT NULL REFERENCES image (id),
    position   SMALLINT NOT NULL CHECK (position BETWEEN 0 AND 9),
    PRIMARY KEY (message_id, position)
);

-- Inbox: one row per recipient. New chapters of one translation collapse into one unread row.
CREATE TABLE notification
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id BIGINT      NOT NULL REFERENCES account (id),
    kind         TEXT        NOT NULL,
    group_key    TEXT,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ
);

CREATE INDEX notification_by_recipient ON notification (recipient_id, id DESC);
CREATE UNIQUE INDEX notification_one_unread_group ON notification (recipient_id, group_key)
    WHERE read_at IS NULL AND group_key IS NOT NULL;
