-- Chapters a reader finished (or marked read). The place in reading_progress says where they
-- are; these say which chapters before it they really read.
CREATE TABLE chapter_read
(
    account_id     BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    edition_id     BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    chapter_number INT         NOT NULL,
    read_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, edition_id, chapter_number)
);

-- Readers so far read in order: what lies before their place counts as read.
INSERT INTO chapter_read (account_id, edition_id, chapter_number, read_at)
SELECT p.account_id, p.edition_id, c.number, p.updated_at
FROM reading_progress p
         JOIN chapter c ON c.edition_id = p.edition_id AND c.published_revision_id IS NOT NULL
WHERE c.number < p.chapter_number OR (c.number = p.chapter_number AND p.position >= 0.9)
ON CONFLICT DO NOTHING;
