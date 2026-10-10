-- A run may be proofreading alone: it edits chapters already published.
ALTER TABLE job DROP CONSTRAINT IF EXISTS job_kind_check;
ALTER TABLE job ADD CONSTRAINT job_kind_check CHECK (kind IN ('analyze', 'translate', 'proofread'));
