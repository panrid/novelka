-- Normalized novel tags: one row per tag, many-to-many links, slug is the case- and space-insensitive key.
CREATE TABLE tags (
    id bigserial PRIMARY KEY,
    name text NOT NULL,
    slug text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE novel_tags (
    novel_id text NOT NULL REFERENCES novels(id),
    tag_id bigint NOT NULL REFERENCES tags(id),
    PRIMARY KEY (novel_id, tag_id)
);
CREATE INDEX novel_tags_tag ON novel_tags(tag_id, novel_id);
-- Standard tag for machine-translated content, whichever tool produced it.
INSERT INTO tags(name, slug) VALUES ('Машинний переклад', 'машинний переклад')
