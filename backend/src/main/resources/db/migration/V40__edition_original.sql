-- Originals a translator pastes or uploads to translate (етап 19): each translation keeps its
-- own, apart from the originals the site reads for the owner (source_chapter). Only the team
-- sees them; readers and search engines never do. A chapter may come from another language
-- than the one before it (an English source first, the Japanese one after it ran out).
CREATE TABLE edition_original
(
    edition_id BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    number     INT         NOT NULL CHECK (number > 0),
    title      TEXT        NOT NULL,
    blocks     JSONB       NOT NULL,
    chars      INT         NOT NULL,              -- characters without spaces: the price in шаги
    language   TEXT        NOT NULL CHECK (language IN ('ja', 'en', 'zh', 'ko')),
    added_by   BIGINT      REFERENCES account (id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (edition_id, number)
);
