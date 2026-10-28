-- Workspace materials: canon, timeline, plans, decisions, retcons, status, notes and attachments (етап 24.2, issue #330).

CREATE TABLE workspace_document
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id  BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    slug        TEXT        NOT NULL,
    title       TEXT        NOT NULL,
    kind        TEXT        NOT NULL CHECK (kind IN ('canon', 'timeline', 'plan', 'decision', 'status', 'meta', 'note')),
    content     TEXT        NOT NULL DEFAULT '',
    version     BIGINT      NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT REFERENCES account (id) ON DELETE SET NULL,
    UNIQUE (edition_id, slug)
);

CREATE INDEX workspace_document_by_edition ON workspace_document (edition_id, kind, updated_at DESC);
CREATE INDEX workspace_document_search ON workspace_document USING gin (to_tsvector('simple', title || ' ' || content));

CREATE TABLE workspace_revision
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id BIGINT      NOT NULL REFERENCES workspace_document (id) ON DELETE CASCADE,
    edition_id  BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    version     BIGINT      NOT NULL,
    title       TEXT        NOT NULL,
    content     TEXT        NOT NULL,
    author_id   BIGINT REFERENCES account (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    comment     TEXT
);

CREATE INDEX workspace_revision_by_doc ON workspace_revision (document_id, created_at DESC);

CREATE TABLE workspace_link
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_document_id    BIGINT NOT NULL REFERENCES workspace_document (id) ON DELETE CASCADE,
    target_document_id    BIGINT REFERENCES workspace_document (id) ON DELETE SET NULL,
    target_chapter_number INT,
    raw_target            TEXT   NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX workspace_link_by_source ON workspace_link (source_document_id);
CREATE INDEX workspace_link_by_target ON workspace_link (target_document_id);

CREATE TABLE workspace_attachment
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id  BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    document_id BIGINT REFERENCES workspace_document (id) ON DELETE SET NULL,
    image_id    BIGINT      NOT NULL REFERENCES image (id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX workspace_attachment_by_edition ON workspace_attachment (edition_id, created_at DESC);
