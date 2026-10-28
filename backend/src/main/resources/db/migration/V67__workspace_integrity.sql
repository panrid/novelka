-- A working file keeps its privacy even after its last attachment is removed.
ALTER TABLE image ADD COLUMN restricted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE image ADD COLUMN workspace_edition_id BIGINT REFERENCES edition(id) ON DELETE SET NULL;
UPDATE image i SET restricted = TRUE,
    workspace_edition_id = (SELECT min(a.edition_id) FROM workspace_attachment a WHERE a.image_id = i.id)
WHERE EXISTS (SELECT 1 FROM workspace_attachment a WHERE a.image_id = i.id);
CREATE INDEX image_workspace ON image(workspace_edition_id) WHERE workspace_edition_id IS NOT NULL;
-- A document version identifies exactly one revision.
CREATE UNIQUE INDEX workspace_revision_version ON workspace_revision(document_id, version);
-- Preserve upload bytes for lossless exports; reader-sized variants remain separate.
ALTER TABLE image ADD COLUMN original_key TEXT;
