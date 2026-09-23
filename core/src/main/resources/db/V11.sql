-- Private drafts of manually published chapters. Publication turns a draft into a normal complete Work revision.
CREATE TABLE manual_drafts (
    novel_id text NOT NULL REFERENCES novels(id),
    chapter integer NOT NULL CHECK (chapter > 0),
    title text NOT NULL,
    text text NOT NULL,
    updated_by text NOT NULL REFERENCES accounts(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (novel_id, chapter)
)
