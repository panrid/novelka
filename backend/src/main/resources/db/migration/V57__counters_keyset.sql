-- Етап 21.5: counters counted ahead instead of per request, and indexes for lists read by key
-- («наступні 20 після глави N»). Triggers keep the counters in the same transaction as the rows
-- they count, whichever code (or cascade) writes them; the backfill counts what is there now.

-- ---- «глав»: the published chapters of a translation, read by key ------------------------------
-- edition.chapter_count is already kept at publication; the list of chapters walks this index.
CREATE INDEX chapter_published ON chapter (edition_id, number) WHERE published_revision_id IS NOT NULL;

-- ---- comments, newest first by key -------------------------------------------------------------
CREATE INDEX comment_roots ON comment (edition_id, chapter_number, id DESC) WHERE reply_to IS NULL;

-- ---- popularity of a translation: readers in the last 30 days ×2 + library entries -------------
CREATE TABLE edition_stat
(
    edition_id    BIGINT PRIMARY KEY REFERENCES edition (id) ON DELETE CASCADE,
    shelved       INT NOT NULL DEFAULT 0, -- in «Читаю», «В планах» or «Прочитано»
    readers_month INT NOT NULL DEFAULT 0, -- readers whose place moved within 30 days
    popularity    INT GENERATED ALWAYS AS (readers_month * 2 + shelved) STORED
);

INSERT INTO edition_stat (edition_id, shelved, readers_month)
SELECT e.id,
       (SELECT count(*) FROM library_entry l WHERE l.edition_id = e.id AND l.list IN ('reading', 'planned', 'done')),
       (SELECT count(*) FROM reading_progress p WHERE p.edition_id = e.id AND p.updated_at > now() - INTERVAL '30 days')
FROM edition e;

CREATE FUNCTION edition_stat_shelved() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP IN ('DELETE', 'UPDATE') AND OLD.list IN ('reading', 'planned', 'done') THEN
        UPDATE edition_stat SET shelved = greatest(shelved - 1, 0) WHERE edition_id = OLD.edition_id;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.list IN ('reading', 'planned', 'done') THEN
        INSERT INTO edition_stat (edition_id, shelved) VALUES (NEW.edition_id, 1)
        ON CONFLICT (edition_id) DO UPDATE SET shelved = edition_stat.shelved + 1;
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER library_entry_counts
    AFTER INSERT OR DELETE OR UPDATE OF list, edition_id
    ON library_entry
    FOR EACH ROW
EXECUTE FUNCTION edition_stat_shelved();

-- A reader counts once they begin, and again when they come back after a month away; those
-- who stayed away longer are taken off by the hourly recount (EditionStats).
CREATE FUNCTION edition_stat_reader() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.updated_at > now() - INTERVAL '30 days' THEN
            UPDATE edition_stat SET readers_month = greatest(readers_month - 1, 0) WHERE edition_id = OLD.edition_id;
        END IF;
        RETURN NULL;
    END IF;
    INSERT INTO edition_stat (edition_id, readers_month) VALUES (NEW.edition_id, 1)
    ON CONFLICT (edition_id) DO UPDATE SET readers_month = edition_stat.readers_month + 1;
    RETURN NULL;
END
$$;

CREATE TRIGGER reading_progress_begun
    AFTER INSERT OR DELETE
    ON reading_progress
    FOR EACH ROW
EXECUTE FUNCTION edition_stat_reader();

CREATE TRIGGER reading_progress_returned
    AFTER UPDATE OF updated_at
    ON reading_progress
    FOR EACH ROW
    WHEN (OLD.updated_at < NEW.updated_at - INTERVAL '30 days')
EXECUTE FUNCTION edition_stat_reader();

-- ---- «прочитано»: chapters a reader marked read in a translation -------------------------------
CREATE TABLE reading_stat
(
    account_id    BIGINT NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    edition_id    BIGINT NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    chapters_read INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, edition_id)
);

INSERT INTO reading_stat (account_id, edition_id, chapters_read)
SELECT account_id, edition_id, count(*)
FROM chapter_read
GROUP BY account_id, edition_id;

-- «Усі до цієї» marks up to 10 000 chapters in one statement: counted once per statement.
CREATE FUNCTION reading_stat_added() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    INSERT INTO reading_stat (account_id, edition_id, chapters_read)
    SELECT account_id, edition_id, count(*) FROM added GROUP BY account_id, edition_id
    ON CONFLICT (account_id, edition_id) DO UPDATE SET chapters_read = reading_stat.chapters_read + excluded.chapters_read;
    RETURN NULL;
END
$$;

CREATE FUNCTION reading_stat_removed() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    UPDATE reading_stat s
    SET chapters_read = greatest(s.chapters_read - r.n, 0)
    FROM (SELECT account_id, edition_id, count(*) AS n FROM removed GROUP BY account_id, edition_id) r
    WHERE s.account_id = r.account_id
      AND s.edition_id = r.edition_id;
    RETURN NULL;
END
$$;

CREATE TRIGGER chapter_read_added
    AFTER INSERT
    ON chapter_read
    REFERENCING NEW TABLE AS added
    FOR EACH STATEMENT
EXECUTE FUNCTION reading_stat_added();

CREATE TRIGGER chapter_read_removed
    AFTER DELETE
    ON chapter_read
    REFERENCING OLD TABLE AS removed
    FOR EACH STATEMENT
EXECUTE FUNCTION reading_stat_removed();

-- ---- «нових слів»: glossary entries of a novel waiting for review ------------------------------
CREATE TABLE novel_stat
(
    novel_id  BIGINT PRIMARY KEY REFERENCES novel (id) ON DELETE CASCADE,
    new_words INT NOT NULL DEFAULT 0
);

INSERT INTO novel_stat (novel_id, new_words)
SELECT n.id, (SELECT count(*) FROM glossary_entry g WHERE g.novel_id = n.id AND g.status = 'new')
FROM novel n;

CREATE FUNCTION novel_stat_words() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP IN ('DELETE', 'UPDATE') AND OLD.status = 'new' THEN
        UPDATE novel_stat SET new_words = greatest(new_words - 1, 0) WHERE novel_id = OLD.novel_id;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.status = 'new' THEN
        INSERT INTO novel_stat (novel_id, new_words) VALUES (NEW.novel_id, 1)
        ON CONFLICT (novel_id) DO UPDATE SET new_words = novel_stat.new_words + 1;
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER glossary_entry_counts
    AFTER INSERT OR DELETE OR UPDATE OF status, novel_id
    ON glossary_entry
    FOR EACH ROW
EXECUTE FUNCTION novel_stat_words();
