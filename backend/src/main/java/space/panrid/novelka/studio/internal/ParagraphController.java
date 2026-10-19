package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.PARAGRAPH_CHECK;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.SUGGESTION;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * What «Як у книжці» marks beside each paragraph of a chapter (етап 20, крок 3): «перевірено» —
 * the team checked it and its text is still the one checked; «машинний» — its text is still what
 * the AI wrote; «є пропозиція» — a reader's правка of it waits. The page compares the texts itself,
 * so an edit not yet published counts too.
 */
@RestController
@RequestMapping("/api/studio/editions/{editionId}/chapters/{number}/paragraphs")
class ParagraphController {

    /** A paragraph and its text: when it was checked, or as the AI wrote it. */
    record ParagraphText(String blockId, String text) {
    }

    record Paragraphs(List<ParagraphText> checked, List<ParagraphText> machine, List<String> offers) {
    }

    record CheckRequest(String text) {
    }

    private static final int TEXT_MAX = 20_000;

    private final AccessPolicy access;
    private final DSLContext db;
    private final JsonMapper json;

    ParagraphController(AccessPolicy access, DSLContext db, JsonMapper json) {
        this.access = access;
        this.db = db;
        this.json = json;
    }

    @GetMapping
    Paragraphs paragraphs(@PathVariable long editionId, @PathVariable int number) {
        access.requireTextEditor(editionId);
        long chapterId = chapterId(editionId, number);
        List<ParagraphText> checked = db.select(PARAGRAPH_CHECK.BLOCK_ID, PARAGRAPH_CHECK.TEXT).from(PARAGRAPH_CHECK)
                .where(PARAGRAPH_CHECK.CHAPTER_ID.eq(chapterId))
                .fetch(row -> new ParagraphText(row.value1(), row.value2()));
        JSONB machine = db.select(REVISION.BLOCKS).from(REVISION)
                .where(REVISION.CHAPTER_ID.eq(chapterId), REVISION.ORIGIN.eq("ai"))
                .orderBy(REVISION.CREATED_AT.desc(), REVISION.ID.desc()).limit(1)
                .fetchOne(REVISION.BLOCKS);
        List<ParagraphText> written = machine == null ? List.of()
                : json.readValue(machine.data(), new TypeReference<List<Block>>() { }).stream()
                        .filter(block -> block.id() != null && !block.text().isBlank())
                        .map(block -> new ParagraphText(block.id(), block.text())).toList();
        List<String> offers = db.selectDistinct(SUGGESTION.BLOCK_ID).from(SUGGESTION)
                .where(SUGGESTION.CHAPTER_ID.eq(chapterId), SUGGESTION.STATE.eq("pending"), SUGGESTION.KIND.eq("block"),
                        SUGGESTION.BLOCK_ID.isNotNull())
                .fetch(SUGGESTION.BLOCK_ID);
        return new Paragraphs(checked, written, offers);
    }

    /** «Позначити перевіреним»: the paragraph as it reads now. */
    @PutMapping("/{blockId}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void check(@PathVariable long editionId, @PathVariable int number, @PathVariable String blockId, @RequestBody CheckRequest body) {
        EditionAccess who = access.requireTextEditor(editionId);
        if (blockId.isBlank() || blockId.length() > 64) {
            throw UserFacingException.badRequest("Такого абзацу немає.");
        }
        String text = body == null || body.text() == null ? "" : body.text();
        if (text.isBlank() || text.length() > TEXT_MAX) {
            throw UserFacingException.badRequest("Порожній абзац не перевіряють.");
        }
        long chapterId = chapterId(editionId, number);
        db.insertInto(PARAGRAPH_CHECK)
                .set(PARAGRAPH_CHECK.CHAPTER_ID, chapterId)
                .set(PARAGRAPH_CHECK.BLOCK_ID, blockId)
                .set(PARAGRAPH_CHECK.TEXT, text)
                .set(PARAGRAPH_CHECK.CHECKED_BY, who.viewer().accountId())
                .onConflict(PARAGRAPH_CHECK.CHAPTER_ID, PARAGRAPH_CHECK.BLOCK_ID).doUpdate()
                .set(PARAGRAPH_CHECK.TEXT, text)
                .set(PARAGRAPH_CHECK.CHECKED_BY, who.viewer().accountId())
                .set(PARAGRAPH_CHECK.CHECKED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    @DeleteMapping("/{blockId}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void uncheck(@PathVariable long editionId, @PathVariable int number, @PathVariable String blockId) {
        access.requireTextEditor(editionId);
        db.deleteFrom(PARAGRAPH_CHECK)
                .where(PARAGRAPH_CHECK.CHAPTER_ID.eq(chapterId(editionId, number)), PARAGRAPH_CHECK.BLOCK_ID.eq(blockId))
                .execute();
    }

    private long chapterId(long editionId, int number) {
        Long id = db.select(CHAPTER.ID).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(number)).fetchOne(CHAPTER.ID);
        if (id == null) {
            throw UserFacingException.notFound("Такої глави немає.");
        }
        return id;
    }
}
