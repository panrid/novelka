-- Suggestions (правки) from readers: a paragraph, «замінити в главі», or a whole chapter
-- from the editor. Collected as drafts, sent as a batch, reviewed in the reader.

CREATE TABLE suggestion_batch
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id   BIGINT      NOT NULL REFERENCES edition (id),
    author_id    BIGINT      NOT NULL REFERENCES account (id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE suggestion
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chapter_id          BIGINT      NOT NULL REFERENCES chapter (id),
    base_revision_id    BIGINT      NOT NULL REFERENCES revision (id),
    author_id           BIGINT      NOT NULL REFERENCES account (id),
    batch_id            BIGINT REFERENCES suggestion_batch (id),
    kind                TEXT        NOT NULL CHECK (kind IN ('block', 'replace', 'chapter')),
    block_id            TEXT,                 -- kind block: the paragraph
    original_text       TEXT,                 -- kind block: its text when the suggestion was made
    proposed            JSONB,                -- block: spans; chapter: blocks and title
    find_text           TEXT CHECK (char_length(find_text) <= 200),
    replacement         TEXT CHECK (char_length(replacement) <= 200),
    note                TEXT CHECK (char_length(note) <= 500),
    state               TEXT        NOT NULL DEFAULT 'draft'
        CHECK (state IN ('draft', 'pending', 'accepted', 'rejected', 'withdrawn', 'stale')),
    reviewer_id         BIGINT REFERENCES account (id),
    reviewed_at         TIMESTAMPTZ,
    review_note         TEXT CHECK (char_length(review_note) <= 500),
    applied_revision_id BIGINT REFERENCES revision (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX suggestion_by_chapter ON suggestion (chapter_id, state);
CREATE INDEX suggestion_by_author ON suggestion (author_id, state, updated_at DESC);

-- A second edit of the same paragraph (or the same «find») replaces the first draft.
CREATE UNIQUE INDEX suggestion_one_draft_per_block ON suggestion (author_id, chapter_id, block_id)
    WHERE state = 'draft' AND kind = 'block';
CREATE UNIQUE INDEX suggestion_one_draft_per_find ON suggestion (author_id, chapter_id, find_text)
    WHERE state = 'draft' AND kind = 'replace';
CREATE UNIQUE INDEX suggestion_one_chapter_draft ON suggestion (author_id, chapter_id)
    WHERE state = 'draft' AND kind = 'chapter';
