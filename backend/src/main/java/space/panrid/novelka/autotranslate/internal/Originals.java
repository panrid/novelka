package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.OriginalFiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The originals a translator pastes or uploads to translate (етап 19), per translation and per
 * chapter, each with its language. Only the team sees them.
 */
@Service
class Originals {

    static final Set<String> LANGUAGES = Set.of("ja", "en", "zh", "ko");
    static final int PAGE = 50;

    private final DSLContext db;
    private final JsonMapper json;
    private final Images images;

    Originals(DSLContext db, JsonMapper json, Images images) {
        this.db = db;
        this.json = json;
        this.images = images;
    }

    /**
     * A chapter of the split, before it is saved: {@code replaces} when the translation has an
     * original with that number already; {@code translated} when the chapter is published.
     */
    record Planned(int number, String title, int chars, int paragraphs, int pictures, String start, boolean replaces,
            boolean translated) {
    }

    record Preview(List<Planned> chapters, int firstNumber, int nextNumber) {
    }

    record Row(int number, String title, int chars, String language, boolean translated, OffsetDateTime updatedAt) {
    }

    record Listing(List<Row> items, int total, int page, int nextNumber, Map<String, Integer> languages) {
    }

    record Saved(int saved, int from, int to) {
    }

    record Original(int number, String title, String language, List<Block> blocks) {
    }

    /**
     * A novel the site did not take from an original's site (one the team added by hand): it is
     * translated from the originals the team pasted.
     */
    static boolean pasted(String novelSource) {
        return "manual".equals(novelSource);
    }

    /** A pasted chapter as the translation reads it; the hash tells whether it changed since its analysis. */
    record Text(int number, String title, String language, List<Block> blocks, int chars, String hash) {
    }

    java.util.Optional<Text> text(long editionId, int number) {
        return db.select(EDITION_ORIGINAL.TITLE, EDITION_ORIGINAL.LANGUAGE, EDITION_ORIGINAL.BLOCKS, EDITION_ORIGINAL.CHARS)
                .from(EDITION_ORIGINAL).where(EDITION_ORIGINAL.EDITION_ID.eq(editionId), EDITION_ORIGINAL.NUMBER.eq(number))
                .fetchOptional(row -> new Text(number, row.value1(), row.value2(),
                        json.readValue(row.value3().data(), new TypeReference<List<Block>>() { }), row.value4(),
                        hash(row.value1() + "\n" + row.value3().data())));
    }

    private static String hash(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Own works have no original; the rest may. */
    void requireTranslation(long editionId) {
        String kind = db.select(EDITION.KIND).from(EDITION).where(EDITION.ID.eq(editionId)).fetchOne(EDITION.KIND);
        if ("original".equals(kind)) {
            throw UserFacingException.badRequest("У власного твору немає оригіналу іншою мовою.");
        }
    }

    static String language(String language) {
        if (language == null || !LANGUAGES.contains(language)) {
            throw UserFacingException.badRequest("Мова оригіналу: японська, англійська, китайська чи корейська.");
        }
        return language;
    }

    /** After the last original or the last chapter, whichever is further. */
    int nextNumber(long editionId) {
        Integer original = db.select(DSL.max(EDITION_ORIGINAL.NUMBER)).from(EDITION_ORIGINAL)
                .where(EDITION_ORIGINAL.EDITION_ID.eq(editionId)).fetchOne(0, Integer.class);
        Integer chapter = db.select(DSL.max(CHAPTER.NUMBER)).from(CHAPTER).where(CHAPTER.EDITION_ID.eq(editionId))
                .fetchOne(0, Integer.class);
        return Math.max(original == null ? 0 : original, chapter == null ? 0 : chapter) + 1;
    }

    Preview preview(long editionId, OriginalFiles.Parsed parsed, Integer first) {
        int next = nextNumber(editionId);
        int from = first == null ? next : checkedFirst(first);
        Set<Integer> stored = numbers(editionId, from, from + parsed.chapters().size() - 1, EDITION_ORIGINAL.EDITION_ID);
        Set<Integer> published = Set.copyOf(db.select(CHAPTER.NUMBER).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.between(from, from + parsed.chapters().size() - 1))
                .fetch(CHAPTER.NUMBER));
        List<Planned> planned = new ArrayList<>();
        for (int i = 0; i < parsed.chapters().size(); i++) {
            OriginalFiles.Chapter chapter = parsed.chapters().get(i);
            int number = from + i;
            String start = chapter.blocks().stream().filter(block -> block.type().equals("paragraph")).map(Block::text)
                    .findFirst().orElse("");
            planned.add(new Planned(number, chapter.title(), chapter.chars(),
                    (int) chapter.blocks().stream().filter(block -> block.type().equals("paragraph")).count(), chapter.pictures(),
                    start.length() > 120 ? start.substring(0, 120) + "…" : start, stored.contains(number), published.contains(number)));
        }
        return new Preview(planned, from, next);
    }

    @Transactional
    Saved save(long editionId, long accountId, OriginalFiles.Parsed parsed, Integer first, String language) {
        String lang = language(language);
        int from = first == null ? nextNumber(editionId) : checkedFirst(first);
        // Each picture of the book is stored once, however many times it is shown.
        Map<String, Long> stored = new HashMap<>();
        for (int i = 0; i < parsed.chapters().size(); i++) {
            OriginalFiles.Chapter chapter = parsed.chapters().get(i);
            List<Block> blocks = new ArrayList<>(chapter.blocks().size());
            for (Block block : chapter.blocks()) {
                if (block.sourceUrl() != null && block.sourceUrl().startsWith(OriginalFiles.EPUB_PICTURE)) {
                    String path = block.sourceUrl().substring(OriginalFiles.EPUB_PICTURE.length());
                    byte[] bytes = parsed.pictures().get(path);
                    if (bytes == null) {
                        continue;
                    }
                    Long imageId = stored.computeIfAbsent(path, key -> images.store(accountId, ImageKind.ILLUSTRATION, bytes,
                            key.substring(key.lastIndexOf('/') + 1)).id());
                    blocks.add(new Block(block.id(), "image", List.of(), imageId, null));
                } else {
                    blocks.add(block);
                }
            }
            JSONB content = JSONB.valueOf(json.writeValueAsString(blocks));
            db.insertInto(EDITION_ORIGINAL)
                    .set(EDITION_ORIGINAL.EDITION_ID, editionId).set(EDITION_ORIGINAL.NUMBER, from + i)
                    .set(EDITION_ORIGINAL.TITLE, chapter.title()).set(EDITION_ORIGINAL.BLOCKS, content)
                    .set(EDITION_ORIGINAL.CHARS, chapter.chars()).set(EDITION_ORIGINAL.LANGUAGE, lang)
                    .set(EDITION_ORIGINAL.ADDED_BY, accountId)
                    .onConflict(EDITION_ORIGINAL.EDITION_ID, EDITION_ORIGINAL.NUMBER).doUpdate()
                    .set(EDITION_ORIGINAL.TITLE, chapter.title()).set(EDITION_ORIGINAL.BLOCKS, content)
                    .set(EDITION_ORIGINAL.CHARS, chapter.chars()).set(EDITION_ORIGINAL.LANGUAGE, lang)
                    .set(EDITION_ORIGINAL.ADDED_BY, accountId).set(EDITION_ORIGINAL.UPDATED_AT, DSL.currentOffsetDateTime())
                    .execute();
        }
        return new Saved(parsed.chapters().size(), from, from + parsed.chapters().size() - 1);
    }

    Listing list(long editionId, int page) {
        int total = db.fetchCount(EDITION_ORIGINAL, EDITION_ORIGINAL.EDITION_ID.eq(editionId));
        int shown = Math.max(1, page);
        var published = DSL.exists(DSL.selectOne().from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(EDITION_ORIGINAL.NUMBER)));
        List<Row> rows = db.select(EDITION_ORIGINAL.NUMBER, EDITION_ORIGINAL.TITLE, EDITION_ORIGINAL.CHARS, EDITION_ORIGINAL.LANGUAGE,
                        DSL.field(published), EDITION_ORIGINAL.UPDATED_AT)
                .from(EDITION_ORIGINAL).where(EDITION_ORIGINAL.EDITION_ID.eq(editionId))
                .orderBy(EDITION_ORIGINAL.NUMBER).limit(PAGE).offset((shown - 1) * PAGE)
                .fetch(r -> new Row(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6()));
        Map<String, Integer> languages = new HashMap<>();
        db.select(EDITION_ORIGINAL.LANGUAGE, DSL.count()).from(EDITION_ORIGINAL).where(EDITION_ORIGINAL.EDITION_ID.eq(editionId))
                .groupBy(EDITION_ORIGINAL.LANGUAGE).forEach(r -> languages.put(r.value1(), r.value2()));
        return new Listing(rows, total, shown, nextNumber(editionId), languages);
    }

    /** The pasted chapters by number, read by key (the API, етап 22.3): those after {@code afterNumber}. */
    List<Row> after(long editionId, Integer afterNumber, int limit) {
        var published = DSL.exists(DSL.selectOne().from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(EDITION_ORIGINAL.NUMBER)));
        return db.select(EDITION_ORIGINAL.NUMBER, EDITION_ORIGINAL.TITLE, EDITION_ORIGINAL.CHARS, EDITION_ORIGINAL.LANGUAGE,
                        DSL.field(published), EDITION_ORIGINAL.UPDATED_AT)
                .from(EDITION_ORIGINAL)
                .where(EDITION_ORIGINAL.EDITION_ID.eq(editionId), afterNumber == null ? DSL.noCondition() : EDITION_ORIGINAL.NUMBER.gt(afterNumber))
                .orderBy(EDITION_ORIGINAL.NUMBER).limit(limit)
                .fetch(r -> new Row(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6()));
    }

    Original find(long editionId, int number) {
        var row = db.select(EDITION_ORIGINAL.TITLE, EDITION_ORIGINAL.LANGUAGE, EDITION_ORIGINAL.BLOCKS).from(EDITION_ORIGINAL)
                .where(EDITION_ORIGINAL.EDITION_ID.eq(editionId), EDITION_ORIGINAL.NUMBER.eq(number)).fetchOne();
        if (row == null) {
            throw UserFacingException.notFound("Оригіналу цієї глави немає.");
        }
        return new Original(number, row.value1(), row.value2(), json.readValue(row.value3().data(), new TypeReference<List<Block>>() { }));
    }

    /** One chapter or a range comes from another language («англійське джерело скінчилось»). */
    int changeLanguage(long editionId, int from, int to, String language) {
        return db.update(EDITION_ORIGINAL).set(EDITION_ORIGINAL.LANGUAGE, language(language))
                .set(EDITION_ORIGINAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(EDITION_ORIGINAL.EDITION_ID.eq(editionId), EDITION_ORIGINAL.NUMBER.between(from, to)).execute();
    }

    /** «Видалити оригінали»: one chapter's, or all of them when {@code number} is null. */
    int delete(long editionId, Integer number) {
        var condition = EDITION_ORIGINAL.EDITION_ID.eq(editionId);
        if (number != null) {
            condition = condition.and(EDITION_ORIGINAL.NUMBER.eq(number));
        }
        return db.deleteFrom(EDITION_ORIGINAL).where(condition).execute();
    }

    private Set<Integer> numbers(long editionId, int from, int to, org.jooq.Field<Long> edition) {
        return Set.copyOf(db.select(EDITION_ORIGINAL.NUMBER).from(EDITION_ORIGINAL)
                .where(edition.eq(editionId), EDITION_ORIGINAL.NUMBER.between(from, to)).fetch(EDITION_ORIGINAL.NUMBER));
    }

    private static int checkedFirst(int first) {
        if (first < 1 || first > 100_000) {
            throw UserFacingException.badRequest("Номер першої глави — від 1.");
        }
        return first;
    }
}
