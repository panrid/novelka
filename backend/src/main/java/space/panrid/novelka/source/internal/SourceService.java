package space.panrid.novelka.source.internal;

import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;

import space.panrid.novelka.jooq.tables.records.SourceChapterRecord;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceNovel;
import space.panrid.novelka.source.SyosetuHttp;
import space.panrid.novelka.source.SourceText;
import space.panrid.novelka.source.Sources;
import space.panrid.novelka.source.SyosetuLink;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
class SourceService implements Sources {

    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final SyosetuHttp http;
    private final JsonMapper json;

    SourceService(DSLContext db, SyosetuHttp http, JsonMapper json) {
        this.db = db;
        this.http = http;
        this.json = json;
    }

    @Override
    public SourceNovel novel(SyosetuLink link) {
        String api = "https://api.syosetu.com/%s/api/?out=json&of=t-w-s-ga-nt-e&ncode=%s"
                .formatted(link.adult() ? "novel18api" : "novelapi", link.code());
        JsonNode answer = json.readTree(http.get(api));
        if (!answer.isArray() || answer.size() < 2) {
            throw UserFacingException.notFound("На Syosetu немає новели з таким посиланням.");
        }
        JsonNode novel = answer.get(1);
        boolean serial = novel.path("noveltype").asInt(1) == 1;
        return new SourceNovel(link, novel.path("title").asString(""), novel.path("writer").asString(""),
                novel.path("story").asString(""), serial ? Math.max(1, novel.path("general_all_no").asInt(1)) : 1,
                serial, novel.path("end").asInt(1) == 0);
    }

    @Override
    public SourceText chapter(long novelId, int number) {
        SourceChapterRecord stored = db.selectFrom(SOURCE_CHAPTER)
                .where(SOURCE_CHAPTER.NOVEL_ID.eq(novelId), SOURCE_CHAPTER.NUMBER.eq(number))
                .fetchOne();
        if (stored != null) {
            return text(stored);
        }
        var novel = db.select(NOVEL.SOURCE_KEY, NOVEL.TITLE_ORIGINAL, NOVEL.SOURCE_CHAPTER_COUNT).from(NOVEL)
                .where(NOVEL.ID.eq(novelId), NOVEL.SOURCE.eq("syosetu")).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Новела не з Syosetu."));
        SyosetuLink link = SyosetuLink.fromKey(novel.value1());
        boolean single = novel.value3() != null && novel.value3() == 1 && number == 1;
        // A short story has no chapter pages: the text is on the novel's own page.
        String html = http.get(single ? link.url() : link.url() + number + "/");
        SyosetuPages.Page page = SyosetuPages.chapter(html, novel.value2() == null ? "" : novel.value2());
        String blocks = json.writeValueAsString(page.blocks());
        db.insertInto(SOURCE_CHAPTER)
                .set(SOURCE_CHAPTER.NOVEL_ID, novelId)
                .set(SOURCE_CHAPTER.NUMBER, number)
                .set(SOURCE_CHAPTER.TITLE, page.title())
                .set(SOURCE_CHAPTER.BLOCKS, JSONB.valueOf(blocks))
                .set(SOURCE_CHAPTER.CHARS, SyosetuPages.chars(page.blocks()))
                .set(SOURCE_CHAPTER.SOURCE_HASH, sha256(blocks))
                .onConflictDoNothing()
                .execute();
        return text(db.selectFrom(SOURCE_CHAPTER)
                .where(SOURCE_CHAPTER.NOVEL_ID.eq(novelId), SOURCE_CHAPTER.NUMBER.eq(number)).fetchSingle());
    }

    private SourceText text(SourceChapterRecord record) {
        return new SourceText(record.getId(), record.getNumber(), record.getTitle(),
                json.readValue(record.getBlocks().data(), BLOCKS), record.getChars(), record.getSourceHash());
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
