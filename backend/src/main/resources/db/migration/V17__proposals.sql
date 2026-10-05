-- «Що перекласти» (рішення 32): anyone with an account proposes a novel by its link, the site
-- translates the title and description at its own cost, the proposer may correct them,
-- others vote, and whoever takes it to translate gets an edition made from it.
CREATE TABLE proposal
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source          TEXT        NOT NULL,
    source_key      TEXT        NOT NULL UNIQUE,
    source_url      TEXT        NOT NULL,
    source_language TEXT,
    title_original  TEXT        NOT NULL,       -- for the import only, never shown
    author_original TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    author          TEXT        NOT NULL DEFAULT '',
    description     JSONB       NOT NULL DEFAULT '[]',
    chapter_count   INT         NOT NULL,
    adult           BOOLEAN     NOT NULL DEFAULT FALSE,
    proposed_by     BIGINT      NOT NULL REFERENCES account (id),
    state           TEXT        NOT NULL DEFAULT 'open' CHECK (state IN ('open', 'taken', 'removed')),
    edition_id      BIGINT REFERENCES edition (id),
    taken_by        BIGINT REFERENCES account (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE proposal_vote
(
    proposal_id BIGINT      NOT NULL REFERENCES proposal (id) ON DELETE CASCADE,
    account_id  BIGINT      NOT NULL REFERENCES account (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (proposal_id, account_id)
);

CREATE INDEX proposal_by_state ON proposal (state, created_at DESC);
