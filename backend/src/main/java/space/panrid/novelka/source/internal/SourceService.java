package space.panrid.novelka.source.internal;

import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;
import static space.panrid.novelka.jooq.Tables.SOURCE_TOC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.jooq.tables.records.SourceChapterRecord;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceEntry;
import space.panrid.novelka.source.SourceLink;
import space.panrid.novelka.source.SourceNovel;
import space.panrid.novelka.source.SourceText;
import space.panrid.novelka.source.Sources;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class SourceService implements Sources {

    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final List<SourceProvider> providers;
    private final JsonMapper json;

    SourceService(DSLContext db, List<SourceProvider> providers, JsonMapper json) {
        this.db = db;
        this.providers = providers;
        this.json = json;
    }

    @Override
    public SourceLink link(String raw) {
        for (SourceProvider provider : providers) {
            Optional<SourceLink> link = provider.parse(raw);
            if (link.isPresent()) {
                return link.get();
            }
        }
        throw UserFacingException.badRequest("Це не посилання на новелу з підтримуваного сайту (%s). Скопіюйте адресу сторінки новели."
                .formatted(String.join(", ", providers.stream().map(SourceProvider::name).toList())));
    }

    @Override
    public SourceNovel novel(SourceLink link) {
        return provider(link.provider()).novel(link);
    }

    @Override
    @Transactional
    public void keep(long novelId, SourceNovel novel) {
        db.deleteFrom(SOURCE_TOC).where(SOURCE_TOC.NOVEL_ID.eq(novelId)).execute();
        var insert = db.insertInto(SOURCE_TOC, SOURCE_TOC.NOVEL_ID, SOURCE_TOC.NUMBER, SOURCE_TOC.REF, SOURCE_TOC.LABEL,
                SOURCE_TOC.TITLE, SOURCE_TOC.VOLUME, SOURCE_TOC.AVAILABLE);
        for (SourceEntry entry : novel.chapters()) {
            insert = insert.values(novelId, entry.number(), entry.ref(), entry.label(), entry.title(), entry.volume(),
                    entry.available());
        }
        if (!novel.chapters().isEmpty()) {
            insert.execute();
        }
    }

    @Override
    public SourceText chapter(long novelId, int number) {
        SourceChapterRecord stored = db.selectFrom(SOURCE_CHAPTER)
                .where(SOURCE_CHAPTER.NOVEL_ID.eq(novelId), SOURCE_CHAPTER.NUMBER.eq(number))
                .fetchOne();
        if (stored != null) {
            return text(stored);
        }
        var novel = db.select(NOVEL.SOURCE, NOVEL.SOURCE_KEY, NOVEL.SOURCE_URL, NOVEL.TITLE_ORIGINAL).from(NOVEL)
                .where(NOVEL.ID.eq(novelId), NOVEL.SOURCE_KEY.isNotNull()).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Новела не з сайту-джерела."));
        SourceEntry entry = db.selectFrom(SOURCE_TOC)
                .where(SOURCE_TOC.NOVEL_ID.eq(novelId), SOURCE_TOC.NUMBER.eq(number)).fetchOptional()
                .map(row -> new SourceEntry(row.getNumber(), row.getRef(), row.getLabel(), row.getTitle(), row.getVolume(),
                        row.getAvailable()))
                .orElseThrow(() -> UserFacingException.notFound("На сайті-джерелі немає глави %d.".formatted(number)));
        if (!entry.available()) {
            throw UserFacingException.badRequest("Глава %s закрита на сайті-джерелі: закритих глав не беремо."
                    .formatted(entry.label() == null ? String.valueOf(number) : entry.label()));
        }
        SourceLink link = new SourceLink(novel.value1(), novel.value2(), novel.value3(), false);
        SourceProvider.Page page = provider(link.provider()).chapter(link, entry);
        String title = !page.title().isBlank() ? page.title()
                : entry.title() != null && !entry.title().isBlank() ? entry.title()
                : novel.value4() == null ? "" : novel.value4();
        String blocks = json.writeValueAsString(page.blocks());
        db.insertInto(SOURCE_CHAPTER)
                .set(SOURCE_CHAPTER.NOVEL_ID, novelId)
                .set(SOURCE_CHAPTER.NUMBER, number)
                .set(SOURCE_CHAPTER.TITLE, title)
                .set(SOURCE_CHAPTER.BLOCKS, JSONB.valueOf(blocks))
                .set(SOURCE_CHAPTER.CHARS, chars(page.blocks()))
                .set(SOURCE_CHAPTER.SOURCE_HASH, sha256(blocks))
                .onConflictDoNothing()
                .execute();
        return text(db.selectFrom(SOURCE_CHAPTER)
                .where(SOURCE_CHAPTER.NOVEL_ID.eq(novelId), SOURCE_CHAPTER.NUMBER.eq(number)).fetchSingle());
    }

    private SourceProvider provider(String id) {
        return providers.stream().filter(provider -> provider.id().equals(id)).findFirst()
                .orElseThrow(() -> UserFacingException.badRequest("Сайт-джерело цієї новели більше не підтримується."));
    }

    private SourceText text(SourceChapterRecord record) {
        return new SourceText(record.getId(), record.getNumber(), record.getTitle(),
                json.readValue(record.getBlocks().data(), BLOCKS), record.getChars(), record.getSourceHash());
    }

    /** Characters without whitespace: what a шаг is measured in. */
    static int chars(List<Block> blocks) {
        int count = 0;
        for (Block block : blocks) {
            String text = block.text();
            for (int i = 0; i < text.length(); ) {
                int point = text.codePointAt(i);
                if (!Character.isWhitespace(point) && point != '　') {
                    count++;
                }
                i += Character.charCount(point);
            }
        }
        return count;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
