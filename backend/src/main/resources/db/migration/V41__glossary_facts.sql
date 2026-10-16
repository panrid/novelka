-- What the novel tells about an entry of its glossary (a person's looks and role, what a town or a
-- thing is), fact by fact with the chapter it comes from: the «хто це» card shows a reader only
-- what is known by their chapter, the team uses it as context (for drawing a scene, say).
CREATE TABLE glossary_fact
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id   BIGINT      NOT NULL REFERENCES glossary_entry (id) ON DELETE CASCADE,
    chapter    INT         NOT NULL CHECK (chapter >= 0),
    fact       TEXT        NOT NULL,
    manual     BOOLEAN     NOT NULL DEFAULT FALSE,
    added_by   BIGINT      REFERENCES account (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX glossary_fact_by_entry ON glossary_fact (entry_id, chapter);
