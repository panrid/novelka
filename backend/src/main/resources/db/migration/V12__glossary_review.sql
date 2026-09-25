-- The owner reviews the glossary: new entries from analysis are approved or rejected.
-- A rejected entry stays (so analysis does not add it again) but never reaches a prompt.
ALTER TABLE glossary_entry
    ADD COLUMN status TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'approved', 'rejected'));

UPDATE glossary_entry SET status = 'approved' WHERE manual;

CREATE INDEX glossary_by_edition ON glossary_entry (edition_id, status, ukrainian);
