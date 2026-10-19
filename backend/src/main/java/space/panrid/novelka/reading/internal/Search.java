package space.panrid.novelka.reading.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.NOVEL_TAG;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.TAG;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import space.panrid.novelka.platform.db.Keyset;

/**
 * The words of a search and where they are looked for (етап 21.10, migration V58).
 *
 * <p>Every word has to be found — in the novel's names or author, the translation's title or the
 * novel's tags — or all of them in one published chapter's title. A word is found when a word of
 * the text begins with it (a Ukrainian ending dropped first: «магії» looks for «маг…»), when it is
 * a part of the text, or, from four letters, when it is close to a word of the text despite a typo
 * (pg_trgm word similarity). Texts and words are compared as {@code search_norm} makes them:
 * lower case, without accents and apostrophes.
 */
final class Search {

    /** More words narrow nothing down and only make the query longer. */
    static final int MAX_WORDS = 6;
    /** Shorter words are typed on the way to a longer one; a typo cannot be told from another word. */
    static final int FUZZY_FROM = 4;
    /** Chapter titles are many: a query this short would match too many of them to be any help. */
    static final int CHAPTERS_FROM = 3;

    private static final Pattern APOSTROPHES = Pattern.compile("['’ʼ‘`´]");
    private static final Pattern DIGIT = Pattern.compile("\\p{Nd}");
    private static final Pattern HAS_LETTER =Pattern.compile("[\\p{L}\\p{Nd}]");
    private static final Pattern NOT_WORD =Pattern.compile("[^\\p{L}\\p{M}\\p{Nd}]+");
    /** Vowels, «ь» and «й» end most Ukrainian word forms: вод-а, вод-и, магі-я, водян-ий. */
    private static final String ENDINGS = "аяоеєиіїуюьй";

    private Search() {
    }

    /** The query's words: letters and digits only, so they are safe inside a tsquery. */
    static List<String> words(String query) {
        if (query == null) {
            return List.of();
        }
        String text = APOSTROPHES.matcher(query.toLowerCase(Locale.ROOT)).replaceAll("");
        return Arrays.stream(NOT_WORD.split(text)).filter(word -> HAS_LETTER.matcher(word).find()).distinct().limit(MAX_WORDS).toList();
    }

    /**
     * A Ukrainian word without its ending, at least three letters long: «магії» → «маг»,
     * «водяний» → «водян», «води» → «вод». Words in other scripts stay as they are.
     */
    static String stem(String word) {
        String stem = word;
        for (int dropped = 0; dropped < 2 && stem.length() > 3 && ENDINGS.indexOf(stem.charAt(stem.length() - 1)) >= 0; dropped++) {
            stem = stem.substring(0, stem.length() - 1);
        }
        return stem;
    }

    static Field<String> norm(Field<String> text) {
        return DSL.function("search_norm", String.class, text);
    }

    static Field<String> norm(String text) {
        return norm(DSL.val(text));
    }

    /** The novel's names and author, as the index {@code novel_search_*} holds them. */
    static Field<String> novelText() {
        return DSL.function("novel_search_text", String.class, NOVEL.TITLE, NOVEL.TITLE_ENGLISH, NOVEL.TITLE_ORIGINAL,
                NOVEL.ALT_TITLES, NOVEL.AUTHOR);
    }

    /** One word in a text already normalised: a word beginning, a part of it, or close despite a typo. */
    static Condition matches(Field<String> text, String word) {
        // Quoted, the word is one lexeme whatever it holds; «:*» makes it a word beginning.
        Condition found = DSL.condition("to_tsvector('simple'::regconfig, {0}) @@ to_tsquery('simple'::regconfig, quote_literal(search_norm({1})) || ':*')",
                text, DSL.val(stem(word)))
                .or(text.like(DSL.inline("%").concat(norm(word)).concat(DSL.inline("%"))));
        // A number with a wrong digit is another number, not a typo.
        if (word.codePointCount(0, word.length()) >= FUZZY_FROM && !DIGIT.matcher(word).find()) {
            found = found.or(DSL.condition("{0} OPERATOR(extensions.%>) {1}", text, norm(word)));
        }
        return found;
    }

    /** All the words in the text. */
    static Condition matchesAll(Field<String> text, List<String> words) {
        return DSL.and(words.stream().map(word -> matches(text, word)).toList());
    }

    /** The word is in the novel's names or author, or in the translation's own title. */
    static Condition named(String word) {
        return matches(novelText(), word).or(EDITION.TITLE.isNotNull().and(matches(norm(EDITION.TITLE), word)));
    }

    /** Novels with a tag the word is in. */
    static Select<Record1<Long>> taggedNovels(String word) {
        return DSL.select(NOVEL_TAG.NOVEL_ID).from(NOVEL_TAG).join(TAG).on(TAG.ID.eq(NOVEL_TAG.TAG_ID))
                .where(matches(norm(TAG.NAME), word));
    }

    /** Editions with a published chapter whose title holds all the words. */
    static Select<Record1<Long>> editionsWithChapter(List<String> words) {
        return DSL.select(CHAPTER.EDITION_ID).from(CHAPTER).join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.DELETED_AT.isNull().and(matchesAll(norm(REVISION.TITLE), words)));
    }

    static boolean searchesChapters(List<String> words) {
        String letters = String.join("", words);
        return letters.codePointCount(0, letters.length()) >= CHAPTERS_FROM;
    }

    /** The catalog's condition for the words; nothing is found by a query without any. */
    static Condition catalog(List<String> words) {
        if (words.isEmpty()) {
            return DSL.falseCondition();
        }
        Condition byNames = DSL.and(words.stream().map(word -> named(word).or(NOVEL.ID.in(taggedNovels(word)))).toList());
        return searchesChapters(words) ? byNames.or(EDITION.ID.in(editionsWithChapter(words))) : byNames;
    }

    /** How close the words are to a text, 0–1: the best match of the whole query to a part of it. */
    static Field<Float> closeness(List<String> words, Field<String> text) {
        return DSL.field("extensions.word_similarity({0}, {1})", Float.class, norm(String.join(" ", words)), DSL.coalesce(text, DSL.inline("")));
    }

    /**
     * Relevance, best first: the title begins with the words, then holds them, then the names hold
     * every word (not only tags or a chapter), then how close the names are despite typos. Keys of
     * a keyset order (етап 21.5): the closeness is rounded to a numeric, so a cursor can hold it
     * exactly and the next page starts right after it.
     */
    static List<Keyset.Key> relevance(List<String> words) {
        Field<String> query = norm(String.join(" ", words));
        Field<String> title = norm(DSL.coalesce(EDITION.TITLE, NOVEL.TITLE));
        Field<Integer> place = DSL.when(title.like(query.concat(DSL.inline("%"))), DSL.inline(3))
                .when(title.like(DSL.inline("%").concat(query).concat(DSL.inline("%"))), DSL.inline(2))
                .when(DSL.and(words.stream().map(Search::named).toList()), DSL.inline(1))
                .otherwise(DSL.inline(0));
        Field<BigDecimal> close = DSL.round(DSL.greatest(closeness(words, novelText()), closeness(words, norm(EDITION.TITLE)))
                .cast(SQLDataType.NUMERIC), 4);
        return List.of(Keyset.Key.desc(place), Keyset.Key.desc(close));
    }
}
