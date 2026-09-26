-- Many source sites (рішення 34): the site is a provider id, and the original's language
-- decides the translation rules. Imported novels so far are all from Syosetu, in Japanese.
ALTER TABLE novel
    DROP CONSTRAINT novel_source_check;
ALTER TABLE novel
    ADD CONSTRAINT novel_source_check CHECK (source ~ '^[a-z0-9]{2,20}$');
ALTER TABLE novel
    ADD COLUMN source_language TEXT CHECK (source_language ~ '^[a-z]{2}$');
UPDATE novel SET source_language = 'ja' WHERE source = 'syosetu';

-- The site's table of contents: the position is the address on our side, the site's own
-- chapter id and the number its readers see (437.2) come with it. Locked chapters are
-- listed so people see them, but never taken.
CREATE TABLE source_toc
(
    novel_id  BIGINT  NOT NULL REFERENCES novel (id),
    number    INT     NOT NULL CHECK (number > 0),
    ref       TEXT    NOT NULL,
    label     TEXT,
    title     TEXT,
    volume    TEXT,
    available BOOLEAN NOT NULL,
    PRIMARY KEY (novel_id, number)
);

-- Syosetu episodes are addressed by their position; a novel of one chapter was read from
-- the novel's own page, as a short story is.
INSERT INTO source_toc (novel_id, number, ref, available)
SELECT novel.id, n, CASE WHEN novel.source_chapter_count = 1 THEN '' ELSE n::TEXT END, TRUE
FROM novel
         CROSS JOIN LATERAL generate_series(1, novel.source_chapter_count) AS n
WHERE novel.source = 'syosetu'
  AND novel.source_chapter_count IS NOT NULL;
