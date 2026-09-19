CREATE TABLE IF NOT EXISTS novel_aliases (
    alias text PRIMARY KEY,
    novel_id text NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS novel_aliases_novel ON novel_aliases(novel_id);
