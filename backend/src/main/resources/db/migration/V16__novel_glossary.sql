-- One glossary per novel for all its translations (рішення 30, етап 14): an entry is a notion
-- with one Ukrainian form; its forms in the originals (涼 in Japanese, Ryo in English) hang
-- off it, one per language, so a novel can move to a source in another language.
ALTER TABLE glossary_entry
    ADD COLUMN novel_id BIGINT REFERENCES novel (id);
UPDATE glossary_entry
SET novel_id = edition.novel_id
FROM edition
WHERE edition.id = glossary_entry.edition_id;

-- Two translations of one novel may both know a name: the older entry stays.
DELETE
FROM glossary_entry newer
    USING glossary_entry older
WHERE newer.novel_id = older.novel_id
  AND newer.japanese = older.japanese
  AND older.id < newer.id;

ALTER TABLE glossary_entry
    ALTER COLUMN novel_id SET NOT NULL;

CREATE TABLE glossary_form
(
    entry_id BIGINT NOT NULL REFERENCES glossary_entry (id) ON DELETE CASCADE,
    novel_id BIGINT NOT NULL REFERENCES novel (id),
    language TEXT   NOT NULL CHECK (language ~ '^[a-z]{2}$'),
    original TEXT   NOT NULL,
    reading  TEXT,
    aliases  JSONB  NOT NULL DEFAULT '[]', -- other spellings in the same original
    PRIMARY KEY (entry_id, language),
    UNIQUE (novel_id, language, original)
);

INSERT INTO glossary_form (entry_id, novel_id, language, original, reading, aliases)
SELECT id, novel_id, 'ja', japanese, reading, aliases
FROM glossary_entry;

-- Dropping the columns drops the old (edition_id, japanese) key and the edition's index.
ALTER TABLE glossary_entry
    DROP COLUMN japanese,
    DROP COLUMN reading,
    DROP COLUMN aliases,
    DROP COLUMN edition_id;

CREATE INDEX glossary_by_novel ON glossary_entry (novel_id, status, ukrainian);
