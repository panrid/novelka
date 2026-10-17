-- Translating originals the team pasted (етап 19, крок 3): a chapter's analysis may come from such
-- an original instead of one the site fetched, known by its hash; a run may leave its chapters as
-- drafts for the one who started it instead of publishing them.
ALTER TABLE chapter_analysis ALTER COLUMN source_chapter_id DROP NOT NULL;
ALTER TABLE chapter_analysis ADD COLUMN original_hash TEXT;
ALTER TABLE job ADD COLUMN publish BOOLEAN NOT NULL DEFAULT TRUE;
