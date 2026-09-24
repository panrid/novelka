-- Team members and roles, chapter drafts of the editor, relay requests, site settings.

CREATE TABLE team_member
(
    team_id    BIGINT      NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    account_id BIGINT      NOT NULL REFERENCES account (id),
    role       TEXT        NOT NULL CHECK (role IN ('translator', 'editor')),
    added_by   BIGINT REFERENCES account (id),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, account_id)
);

CREATE INDEX team_member_by_account ON team_member (account_id);

-- Team names are unique among named teams, whatever the letter case (рішення 11).
ALTER TABLE team ADD COLUMN name_key TEXT;
CREATE UNIQUE INDEX team_name_unique ON team (name_key) WHERE name_key IS NOT NULL;

-- What someone is typing in the chapter editor before publishing. One per person and chapter.
CREATE TABLE editor_draft
(
    chapter_id       BIGINT      NOT NULL REFERENCES chapter (id) ON DELETE CASCADE,
    account_id       BIGINT      NOT NULL REFERENCES account (id),
    base_revision_id BIGINT REFERENCES revision (id),
    title            TEXT        NOT NULL DEFAULT '',
    blocks           JSONB       NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chapter_id, account_id)
);

-- «Хочу продовжити»: another team asks to continue a translation (рішення 4).
CREATE TABLE takeover_request
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id   BIGINT      NOT NULL REFERENCES edition (id),
    team_id      BIGINT      NOT NULL REFERENCES team (id),
    requested_by BIGINT      NOT NULL REFERENCES account (id),
    message      TEXT CHECK (char_length(message) <= 1000),
    state        TEXT        NOT NULL DEFAULT 'open'
        CHECK (state IN ('open', 'declined', 'granted', 'withdrawn')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX takeover_one_open_per_team ON takeover_request (edition_id, team_id) WHERE state = 'open';

-- Settings the site owner changes without a redeploy. Missing keys use code defaults.
CREATE TABLE site_setting
(
    key        TEXT PRIMARY KEY,
    value      JSONB       NOT NULL,
    updated_by BIGINT REFERENCES account (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
