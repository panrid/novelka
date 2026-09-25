-- The number a chapter shows («0», «31.1», or none for «Пролог») is separate from its
-- position, which stays the address: /n/slug/32 may show «31.1. …».
-- NULL: show the position; '': show no number.
ALTER TABLE chapter
    ADD COLUMN label TEXT CHECK (label IS NULL OR label = '' OR label ~ '^[0-9]{1,5}(\.[0-9]{1,3})?$');

-- Analysis can run on its own ahead of translation, so the glossary and chapter titles
-- are checked before any chapter is translated.
ALTER TABLE job
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'translate' CHECK (kind IN ('analyze', 'translate'));

CREATE TABLE chapter_analysis
(
    edition_id        BIGINT      NOT NULL REFERENCES edition (id),
    number            INT         NOT NULL,
    source_chapter_id BIGINT      NOT NULL REFERENCES source_chapter (id),
    title             TEXT        NOT NULL,          -- Ukrainian, without the number
    label             TEXT CHECK (label IS NULL OR label = '' OR label ~ '^[0-9]{1,5}(\.[0-9]{1,3})?$'),
    job_id            BIGINT REFERENCES job (id),
    edited            BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (edition_id, number)
);
