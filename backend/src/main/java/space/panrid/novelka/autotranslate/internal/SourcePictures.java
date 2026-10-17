package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.Chapters;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The author's pictures of the original's site in chapters already published: a picture the
 * translation lacks is copied to the site and put where the original has it — after the
 * paragraph it follows there. Chapters translated while the site could not follow the picture
 * links (mitemin answers with a redirect) came out without them.
 */
@Component
class SourcePictures {

    private static final Logger log = LoggerFactory.getLogger(SourcePictures.class);
    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final Chapters chapters;
    private final Images images;
    private final JsonMapper json;

    SourcePictures(DSLContext db, Chapters chapters, Images images, JsonMapper json) {
        this.db = db;
        this.chapters = chapters;
        this.images = images;
        this.json = json;
    }

    /** A published chapter and how many of the original's pictures it lacks. */
    record Missing(int number, int pictures) {
    }

    record Filled(int added, int failed) {
    }

    List<Missing> missing(long editionId) {
        List<Missing> out = new ArrayList<>();
        db.select(CHAPTER.NUMBER, SOURCE_CHAPTER.BLOCKS).from(CHAPTER).join(SOURCE_CHAPTER).on(SOURCE_CHAPTER.ID.eq(CHAPTER.SOURCE_CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.PUBLISHED_REVISION_ID.isNotNull())
                .orderBy(CHAPTER.NUMBER)
                .forEach(r -> {
                    List<Block> pictures = pictures(r.value2());
                    if (pictures.isEmpty()) {
                        return;
                    }
                    Set<String> present = ids(chapters.current(editionId, r.value1()).blocks());
                    long lacking = pictures.stream().filter(block -> !present.contains(block.id())).count();
                    if (lacking > 0) {
                        out.add(new Missing(r.value1(), (int) lacking));
                    }
                });
        return out;
    }

    /** Copies the pictures chapter {@code number} lacks and publishes them as a new revision by {@code accountId}. */
    Filled fill(long editionId, int number, long accountId) {
        JSONB source = db.select(SOURCE_CHAPTER.BLOCKS).from(CHAPTER).join(SOURCE_CHAPTER).on(SOURCE_CHAPTER.ID.eq(CHAPTER.SOURCE_CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(number)).fetchOne(SOURCE_CHAPTER.BLOCKS);
        if (source == null) {
            throw UserFacingException.notFound("У цієї глави немає оригіналу з сайту.");
        }
        var current = chapters.current(editionId, number);
        List<Block> original = json.readValue(source.data(), BLOCKS);
        List<Block> text = new ArrayList<>(current.blocks());
        Set<String> present = ids(text);
        int added = 0;
        int failed = 0;
        for (int at = 0; at < original.size(); at++) {
            Block block = original.get(at);
            if (!"image".equals(block.type()) || block.sourceUrl() == null || present.contains(block.id())) {
                continue;
            }
            long imageId;
            try {
                imageId = images.storeFromUrl(accountId, ImageKind.ILLUSTRATION, block.sourceUrl()).id();
            } catch (UserFacingException unavailable) {
                log.info("Picture {} of chapter {} skipped: {}", block.sourceUrl(), number, unavailable.getMessage());
                failed++;
                continue;
            }
            text.add(place(original, at, text), new Block(block.id(), "image", List.of(), imageId, null));
            present.add(block.id());
            added++;
        }
        if (added > 0) {
            chapters.publish(editionId, number, accountId, current.title(), text, current.revisionId(), true);
        }
        return new Filled(added, failed);
    }

    /** Right after the nearest block before it in the original that the translation has; the very start if none. */
    static int place(List<Block> original, int at, List<Block> text) {
        for (int before = at - 1; before >= 0; before--) {
            String id = original.get(before).id();
            for (int i = 0; i < text.size(); i++) {
                if (text.get(i).id().equals(id)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    private List<Block> pictures(JSONB blocks) {
        return blocks == null ? List.of() : json.readValue(blocks.data(), BLOCKS).stream()
                .filter(block -> "image".equals(block.type()) && block.sourceUrl() != null).toList();
    }

    private static Set<String> ids(List<Block> blocks) {
        Set<String> out = new HashSet<>();
        blocks.forEach(block -> out.add(block.id()));
        return out;
    }
}
