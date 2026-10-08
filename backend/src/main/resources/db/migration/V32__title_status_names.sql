-- How the original stands and its other names; until when a paused translation rests.
ALTER TABLE novel
    ADD COLUMN title_english TEXT,
    ADD COLUMN alt_titles    TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN source_status TEXT CHECK (source_status IN ('ongoing', 'completed', 'paused'));

ALTER TABLE edition
    ADD COLUMN paused_until DATE;
