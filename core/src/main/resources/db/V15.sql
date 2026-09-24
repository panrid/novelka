-- Corrections start as private drafts and are submitted in batches. A batch is approved as one revision per chapter.
ALTER TABLE corrections DROP CONSTRAINT corrections_state_check;
ALTER TABLE corrections ADD CONSTRAINT corrections_state_check CHECK (state IN ('draft','pending','approved','rejected'));
-- kind=replace replaces every occurrence of original with replacement in one chapter or in the whole novel.
ALTER TABLE corrections ADD COLUMN kind text NOT NULL DEFAULT 'block' CHECK (kind IN ('block','replace'));
ALTER TABLE corrections ADD COLUMN scope text NOT NULL DEFAULT 'chapter' CHECK (scope IN ('chapter','novel'));
ALTER TABLE corrections ADD COLUMN batch_id text;
ALTER TABLE corrections ADD COLUMN updated_at timestamptz;
DROP INDEX corrections_pending_block;
CREATE UNIQUE INDEX corrections_open_block ON corrections(author_id,novel_id,chapter,block_id) WHERE state IN ('draft','pending') AND kind='block';
CREATE INDEX corrections_batch ON corrections(batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX corrections_author_drafts ON corrections(author_id,novel_id) WHERE state='draft'
