-- Search (етап 21.10): PostgreSQL full text and trigrams instead of ILIKE over every row.
--
-- Stock PostgreSQL has no Ukrainian dictionary (no 'ukrainian' text search configuration), so
-- texts are indexed with the 'simple' one: words as they are, lower-cased, accents dropped. Word
-- forms are met halfway by the query: «магії» drops its vowel ending and is looked for as the
-- beginning of a word («маг:*»), and pg_trgm forgives typos («чаривниця» finds «Чарівниця»).
--
-- The extensions live in their own schema: jOOQ generates classes only for «public».
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA extensions;

-- The form texts are indexed and queries are compared in: lower case, no accents («Maō» is
-- «mao»), «ґ» as «г», «ё» as «е», and no apostrophes, so «пам'ять», «пам’ять» and «память» meet.
-- unaccent() is only STABLE (its dictionary could change); with the dictionary named here the
-- result never changes, which lets indexes use it.
CREATE FUNCTION search_norm(t TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN lower(extensions.unaccent('extensions.unaccent'::regdictionary,
                                     translate(t, 'ҐґЁё''’ʼ‘`´', 'ГгЕе')));

-- Everything a novel is known by: its title, English and original titles, other names, author.
CREATE FUNCTION novel_search_text(title TEXT, title_english TEXT, title_original TEXT, alt_titles TEXT[], author TEXT)
    RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN search_norm(concat_ws(' ', title, title_english, title_original, array_to_string(alt_titles, ' '), author));

-- Each searched text has two indexes: words (full text, «маг:*») and trigrams (typos and parts
-- of words, LIKE '%…%').
CREATE INDEX novel_search_words ON novel
    USING gin (to_tsvector('simple'::regconfig, novel_search_text(title, title_english, title_original, alt_titles, author)));
CREATE INDEX novel_search_trigrams ON novel
    USING gin (novel_search_text(title, title_english, title_original, alt_titles, author) extensions.gin_trgm_ops);

-- A team's own title for its translation.
CREATE INDEX edition_search_words ON edition
    USING gin (to_tsvector('simple'::regconfig, search_norm(title))) WHERE title IS NOT NULL;
CREATE INDEX edition_search_trigrams ON edition
    USING gin (search_norm(title) extensions.gin_trgm_ops) WHERE title IS NOT NULL;

CREATE INDEX tag_search_words ON tag USING gin (to_tsvector('simple'::regconfig, search_norm(name)));
CREATE INDEX tag_search_trigrams ON tag USING gin (search_norm(name) extensions.gin_trgm_ops);

-- Chapter titles: a revision's title, found through the chapter that publishes it.
CREATE INDEX revision_search_words ON revision USING gin (to_tsvector('simple'::regconfig, search_norm(title)));
CREATE INDEX revision_search_trigrams ON revision USING gin (search_norm(title) extensions.gin_trgm_ops);
CREATE INDEX chapter_by_published_revision ON chapter (published_revision_id) WHERE published_revision_id IS NOT NULL;
