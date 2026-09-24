-- Novels, editions (a team's translation or an original work), chapters with immutable
-- revisions, and what readers keep: library lists and reading progress.

-- Teams: only what editions need now. Members and roles arrive with the Studio (stage 3).
CREATE TABLE team
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT,                                  -- NULL: shown as the owner's nick
    handle     TEXT        NOT NULL,                  -- for $mentions and /team/{handle}
    handle_key TEXT        NOT NULL UNIQUE,           -- lower(handle)
    owner_id   BIGINT      NOT NULL REFERENCES account (id),
    personal   BOOLEAN     NOT NULL DEFAULT FALSE,    -- the team everyone gets by default
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX team_one_personal_per_owner ON team (owner_id) WHERE personal;

ALTER TABLE image
    ADD CONSTRAINT image_team_fk FOREIGN KEY (team_id) REFERENCES team (id);

CREATE TABLE tag
(
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE -- normalised name: «Фентезі» and « фентезі » are one tag
);

-- The work itself. Japanese fields are for the AI only and never leave the server.
CREATE TABLE novel
(
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source               TEXT        NOT NULL CHECK (source IN ('syosetu', 'manual', 'original')),
    source_key           TEXT UNIQUE,
    source_url           TEXT,
    title_original       TEXT,
    author_original      TEXT,
    author_account_id    BIGINT REFERENCES account (id), -- for original works
    title                TEXT        NOT NULL,
    author               TEXT        NOT NULL DEFAULT '',
    description          JSONB       NOT NULL DEFAULT '[]',
    source_chapter_count INT,
    slug                 TEXT        NOT NULL UNIQUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE novel_tag
(
    novel_id BIGINT NOT NULL REFERENCES novel (id) ON DELETE CASCADE,
    tag_id   BIGINT NOT NULL REFERENCES tag (id),
    PRIMARY KEY (novel_id, tag_id)
);

CREATE INDEX novel_tag_by_tag ON novel_tag (tag_id);

-- One team's version of a novel: a translation, or the author's own work.
CREATE TABLE edition
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    novel_id               BIGINT      NOT NULL REFERENCES novel (id),
    team_id                BIGINT      NOT NULL REFERENCES team (id),
    title                  TEXT,                     -- NULL: the novel's title
    description            JSONB,                    -- NULL: the novel's description
    cover_image_id         BIGINT REFERENCES image (id),
    kind                   TEXT        NOT NULL CHECK (kind IN ('human', 'machine', 'mixed', 'original')),
    status                 TEXT        NOT NULL DEFAULT 'ongoing'
        CHECK (status IN ('ongoing', 'completed', 'paused', 'abandoned')),
    adult                  BOOLEAN     NOT NULL DEFAULT FALSE,
    continues_edition_id   BIGINT REFERENCES edition (id),
    first_number           INT         NOT NULL DEFAULT 1,
    -- Paid chapters come after launch (stage 12); until then everything is free.
    access_mode            TEXT        NOT NULL DEFAULT 'free' CHECK (access_mode IN ('free', 'paid', 'early')),
    access_price_shah      INT,
    access_pack_size       INT,
    access_free_after_days INT,
    free_first_chapters    INT         NOT NULL DEFAULT 0,
    chapter_count          INT         NOT NULL DEFAULT 0, -- published chapters, kept by the text module
    last_published_at      TIMESTAMPTZ,
    hidden_at              TIMESTAMPTZ,
    hidden_reason          TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (novel_id, team_id)
);

CREATE INDEX edition_recently_published ON edition (last_published_at DESC) WHERE hidden_at IS NULL;

CREATE TABLE chapter
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id            BIGINT      NOT NULL REFERENCES edition (id),
    number                INT         NOT NULL CHECK (number > 0),
    source_chapter_id     BIGINT,                   -- FK arrives with the Syosetu import
    source_chars          INT,                      -- length of the original, for pricing in shags
    access_override       TEXT CHECK (access_override IN ('free', 'paid', 'early')),
    published_revision_id BIGINT,
    first_published_at    TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (edition_id, number)
);

CREATE INDEX chapter_recently_published ON chapter (edition_id, first_published_at DESC);

-- Revisions never change. Publishing moves chapter.published_revision_id.
CREATE TABLE revision
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chapter_id BIGINT      NOT NULL REFERENCES chapter (id),
    parent_id  BIGINT REFERENCES revision (id),
    title      TEXT        NOT NULL,
    blocks     JSONB       NOT NULL,
    origin     TEXT        NOT NULL CHECK (origin IN ('ai', 'editor', 'suggestion', 'replace', 'import')),
    author_id  BIGINT REFERENCES account (id),
    job_id     BIGINT,
    source_hash TEXT,
    stats      JSONB       NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX revision_by_chapter ON revision (chapter_id, created_at DESC);

ALTER TABLE chapter
    ADD CONSTRAINT chapter_published_revision_fk FOREIGN KEY (published_revision_id) REFERENCES revision (id);

CREATE TABLE contribution
(
    revision_id    BIGINT NOT NULL REFERENCES revision (id),
    account_id     BIGINT NOT NULL REFERENCES account (id),
    blocks_changed INT    NOT NULL,
    chars_changed  INT    NOT NULL,
    PRIMARY KEY (revision_id, account_id)
);

CREATE TABLE library_entry
(
    account_id BIGINT      NOT NULL REFERENCES account (id),
    edition_id BIGINT      NOT NULL REFERENCES edition (id),
    list       TEXT        NOT NULL CHECK (list IN ('reading', 'planned', 'done', 'paused', 'dropped')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, edition_id)
);

CREATE INDEX library_entry_by_edition ON library_entry (edition_id);

-- Where a reader stopped, synced between devices.
CREATE TABLE reading_progress
(
    account_id     BIGINT      NOT NULL REFERENCES account (id),
    edition_id     BIGINT      NOT NULL REFERENCES edition (id),
    chapter_number INT         NOT NULL,
    position       REAL        NOT NULL DEFAULT 0 CHECK (position BETWEEN 0 AND 1),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, edition_id)
);

CREATE INDEX reading_progress_recent ON reading_progress (edition_id, updated_at DESC);
CREATE INDEX reading_progress_by_reader ON reading_progress (account_id, updated_at DESC);
