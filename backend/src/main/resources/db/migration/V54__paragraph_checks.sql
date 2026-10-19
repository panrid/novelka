-- «Як у книжці» (етап 20, крок 3): the team marks a paragraph of the translation «перевірено». The
-- mark holds while the paragraph's text stays what it was when marked; an edit makes it unchecked again.
CREATE TABLE paragraph_check
(
    chapter_id BIGINT      NOT NULL REFERENCES chapter (id) ON DELETE CASCADE,
    block_id   TEXT        NOT NULL CHECK (char_length(block_id) BETWEEN 1 AND 64),
    text       TEXT        NOT NULL,
    checked_by BIGINT      REFERENCES account (id) ON DELETE SET NULL,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chapter_id, block_id)
);
