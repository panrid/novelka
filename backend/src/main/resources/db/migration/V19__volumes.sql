-- Volumes (етап 15): a volume is a run of chapters from its first one to the next volume.
-- Only an ordinary volume counts in the numbering; a prologue, side stories and extras show
-- their chapters without a number. The address (/n/slug/32) never changes.
CREATE TABLE volume
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id   BIGINT NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    first_number INT    NOT NULL CHECK (first_number > 0),
    title        TEXT   NOT NULL DEFAULT '',
    kind         TEXT   NOT NULL CHECK (kind IN ('volume', 'prologue', 'side', 'extra')),
    UNIQUE (edition_id, first_number)
);

-- Automatic numbers run through all volumes or start from 1 in each one.
ALTER TABLE edition
    ADD COLUMN numbering TEXT NOT NULL DEFAULT 'continuous' CHECK (numbering IN ('continuous', 'per_volume'));

-- chapter.label stays what readers see. A number someone typed (or the analysis took from the
-- original) is kept; others are worked out again whenever the structure changes.
ALTER TABLE chapter
    ADD COLUMN label_manual BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE chapter SET label_manual = TRUE WHERE label IS NOT NULL;
