-- How often a chapter names each character of the glossary («Персонажі» sorted by mentions, up
-- to the reader's chapter): counted once per chapter from its original (or, without one, from
-- the translation) and kept; counted again when the characters' names or the text changed since.
CREATE TABLE glossary_mention
(
    edition_id     BIGINT NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    chapter_number INT    NOT NULL,
    entry_id       BIGINT NOT NULL REFERENCES glossary_entry (id) ON DELETE CASCADE,
    mentions       INT    NOT NULL,
    PRIMARY KEY (edition_id, chapter_number, entry_id)
);

-- fingerprint: of the names and of the text the chapter was counted with.
CREATE TABLE glossary_mention_scan
(
    edition_id     BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    chapter_number INT         NOT NULL,
    fingerprint    INT         NOT NULL,
    scanned_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (edition_id, chapter_number)
);
