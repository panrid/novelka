-- A novel may be proposed from any site, or with no link at all: then the proposer gives its
-- name (and, if they like, a description and a comment). Only sites the site can read fill
-- everything in by themselves and offer autotranslation once a team takes the novel.
--   source = 'syosetu' (or another site the site reads): as before;
--   source = 'link':   a link the site cannot read; source_key is the link made comparable;
--   source = 'manual': no link.
ALTER TABLE proposal
    ALTER COLUMN source_key DROP NOT NULL,
    ALTER COLUMN source_url DROP NOT NULL,
    ALTER COLUMN chapter_count DROP NOT NULL,
    ADD COLUMN comment TEXT NOT NULL DEFAULT '' CHECK (char_length(comment) <= 2000);
