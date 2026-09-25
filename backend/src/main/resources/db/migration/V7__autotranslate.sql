-- Autotranslation: originals from Syosetu, the job queue in PostgreSQL, the journal of
-- every AI call (for money and for recovery), and the per-edition glossary.

CREATE TABLE source_chapter
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    novel_id    BIGINT      NOT NULL REFERENCES novel (id),
    number      INT         NOT NULL CHECK (number > 0),
    title       TEXT        NOT NULL,        -- Japanese: for the AI only, never shown
    blocks      JSONB       NOT NULL,
    chars       INT         NOT NULL,        -- characters without spaces: the price in shags
    source_hash TEXT        NOT NULL,
    fetched_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (novel_id, number)
);

ALTER TABLE chapter
    ADD CONSTRAINT chapter_source_fk FOREIGN KEY (source_chapter_id) REFERENCES source_chapter (id);

CREATE TABLE job
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id     BIGINT      NOT NULL REFERENCES edition (id),
    requested_by   BIGINT      NOT NULL REFERENCES account (id),
    first_number   INT         NOT NULL,
    last_number    INT         NOT NULL,
    state          TEXT        NOT NULL DEFAULT 'queued'
        CHECK (state IN ('queued', 'running', 'done', 'failed', 'cancelled')),
    funding        TEXT        NOT NULL DEFAULT 'site' CHECK (funding IN ('site', 'team')),
    quote_shah     INT         NOT NULL,
    charged_shah   INT         NOT NULL DEFAULT 0,
    hold_tx_id     BIGINT,
    settings       JSONB       NOT NULL,     -- model and prices at launch
    error          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ
);

CREATE INDEX job_by_edition ON job (edition_id, created_at DESC);

-- One step per chapter; stage results are checkpoints, so a restart continues where it stopped.
CREATE TABLE job_step
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id         BIGINT      NOT NULL REFERENCES job (id),
    chapter_number INT         NOT NULL,
    stage          TEXT        NOT NULL DEFAULT 'fetch'
        CHECK (stage IN ('fetch', 'analyze', 'translate', 'proofread', 'publish', 'done')),
    state          TEXT        NOT NULL DEFAULT 'pending'
        CHECK (state IN ('pending', 'running', 'done', 'failed', 'cancelled')),
    attempts       INT         NOT NULL DEFAULT 0,
    not_before     TIMESTAMPTZ NOT NULL DEFAULT now(),
    checkpoint     JSONB       NOT NULL DEFAULT '{}',
    error          TEXT,
    error_reason   TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (job_id, chapter_number)
);

CREATE INDEX job_step_ready ON job_step (state, not_before) WHERE state = 'pending';

CREATE TABLE ai_call
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id              BIGINT REFERENCES job (id),
    chapter_number      INT,
    stage               TEXT        NOT NULL,
    segment             INT,
    model               TEXT        NOT NULL,
    request_hash        TEXT        NOT NULL,
    request             JSONB       NOT NULL,
    response            JSONB,
    state               TEXT        NOT NULL DEFAULT 'pending'
        CHECK (state IN ('pending', 'complete', 'failed', 'uncertain')),
    cost_estimated_musd BIGINT      NOT NULL,
    cost_actual_musd    BIGINT,
    tokens_in           INT,
    tokens_out          INT,
    error               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ
);

CREATE INDEX ai_call_reuse ON ai_call (request_hash) WHERE state = 'complete';
CREATE INDEX ai_call_by_job ON ai_call (job_id, chapter_number);
CREATE INDEX ai_call_recent ON ai_call (created_at DESC);

-- Names and terms of one edition, so «Рьо» stays «Рьо» from chapter to chapter.
CREATE TABLE glossary_entry
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id     BIGINT      NOT NULL REFERENCES edition (id),
    japanese       TEXT        NOT NULL,
    reading        TEXT,
    ukrainian      TEXT        NOT NULL,
    aliases        JSONB       NOT NULL DEFAULT '[]', -- other spellings in the original
    kind           TEXT        NOT NULL DEFAULT 'term'
        CHECK (kind IN ('character', 'place', 'organization', 'term', 'other')),
    gender         TEXT CHECK (gender IN ('male', 'female', 'unknown')),
    note           TEXT,
    source_chapter INT,
    manual         BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (edition_id, japanese)
);
