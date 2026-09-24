-- Uploaded pictures (avatars now; covers, illustrations and message pictures later)
-- and confirming a new email before it replaces the old one.

CREATE TABLE image
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_account_id BIGINT      NOT NULL REFERENCES account (id),
    team_id          BIGINT,                       -- FK arrives with teams
    kind             TEXT        NOT NULL
        CHECK (kind IN ('avatar', 'group_avatar', 'cover', 'illustration', 'message')),
    variants         JSONB       NOT NULL,         -- {"96": "2026/09/ab12-96.jpg", ...}
    mime             TEXT        NOT NULL,
    width            INT         NOT NULL,
    height           INT         NOT NULL,
    sha256           TEXT        NOT NULL,
    source_url       TEXT,                         -- when added by a link
    hidden_at        TIMESTAMPTZ,
    hidden_by        BIGINT REFERENCES account (id),
    hidden_reason    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX image_by_owner ON image (owner_account_id, created_at);

ALTER TABLE account
    ADD CONSTRAINT account_avatar_fk FOREIGN KEY (avatar_image_id) REFERENCES image (id) ON DELETE SET NULL;

ALTER TABLE email_token
    DROP CONSTRAINT email_token_purpose_check,
    ADD CONSTRAINT email_token_purpose_check CHECK (purpose IN ('verify', 'reset', 'change_email'));
