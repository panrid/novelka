-- A word of a translated paragraph tied to a glossary entry by the team, or untied from it
-- (linked = false): «до» in this paragraph is a conjunction, not the kendo strike. Without a row
-- the reader's hints follow the original — an entry counts in a paragraph whose original names it.
-- A row is matched by its words in the paragraph; once they are gone from it, it does nothing.
CREATE TABLE glossary_link
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id     BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    chapter_number INT         NOT NULL,
    block_id       TEXT        NOT NULL,
    entry_id       BIGINT      NOT NULL REFERENCES glossary_entry (id) ON DELETE CASCADE,
    form           TEXT        NOT NULL,
    linked         BOOLEAN     NOT NULL,
    added_by       BIGINT      REFERENCES account (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (edition_id, chapter_number, block_id, entry_id, form)
);

CREATE INDEX glossary_link_by_chapter ON glossary_link (edition_id, chapter_number);
