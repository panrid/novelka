-- Discussion of a novel (chapter 0) or one chapter. Deletion is soft so moderation stays auditable.
CREATE TABLE comments (
    id bigserial PRIMARY KEY,
    novel_id text NOT NULL REFERENCES novels(id),
    chapter integer NOT NULL DEFAULT 0 CHECK (chapter >= 0),
    author_id text NOT NULL REFERENCES accounts(id),
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    edited_at timestamptz,
    deleted_at timestamptz,
    deleted_by text REFERENCES accounts(id)
);
CREATE INDEX comments_thread ON comments(novel_id, chapter, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX comments_author_created ON comments(author_id, created_at DESC)
