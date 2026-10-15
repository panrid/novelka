package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.REVISION;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Service;

import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.BlockRules;
import space.panrid.novelka.text.Chapters;
import space.panrid.novelka.text.EditorModels;

/**
 * «Мої картинки»: every illustration a person drew on the site or uploaded, kept until they
 * put it away, and where each one is used. Pictures named like «27-5.png» can be placed into
 * a novel all at once: chapter 27, after its fifth paragraph.
 */
@Service
class PictureLibrary {

    static final int PAGE = 40;

    /** «27-5.png», «027-05.jpg», «c027-s5.png», «27_5 (2).png»: chapter, then paragraph. */
    private static final Pattern PLACE = Pattern.compile("^[a-zA-Z]*0*(\\d{1,5})[-_ ][a-zA-Z]*0*(\\d{1,5})(?!\\d)");

    record Use(long editionId, String novelSlug, String title, int number) {
    }

    record Picture(long id, String url, String fullUrl, int width, int height, String fileName, boolean drawn,
            String prompt, String fragment, String sourceUrl, OffsetDateTime createdAt, List<Use> uses) {
    }

    record Page(List<Picture> items, int total, int page) {
    }

    /**
     * Where one picture would go.
     *
     * @param problem why it will not be placed, or null
     */
    record Place(long imageId, String fileName, String url, Integer chapter, Integer paragraph, String quote, String problem) {
    }

    record Placed(int chapters, int pictures, List<String> skipped) {
    }

    private final DSLContext db;
    private final Images images;
    private final Chapters chapters;
    private final Clock clock;

    PictureLibrary(DSLContext db, Images images, Chapters chapters, Clock clock) {
        this.db = db;
        this.images = images;
        this.chapters = chapters;
        this.clock = clock;
    }

    /** @param filter «drawn», «uploaded», «unused»; anything else: all */
    Page page(long accountId, String filter, int page) {
        int at = Math.max(1, page);
        Condition where = mine(accountId);
        switch (filter == null ? "" : filter) {
            case "drawn" -> where = where.and(IMAGE.AI_CALL_ID.isNotNull());
            case "uploaded" -> where = where.and(IMAGE.AI_CALL_ID.isNull());
            case "unused" -> where = where.and(IMAGE.ID.notIn(usedPictures()));
            default -> { }
        }
        int total = db.fetchCount(IMAGE, where);
        var rows = db.select(IMAGE.ID, IMAGE.WIDTH, IMAGE.HEIGHT, IMAGE.FILE_NAME, IMAGE.AI_CALL_ID, IMAGE.PROMPT, IMAGE.FRAGMENT,
                        IMAGE.SOURCE_URL, IMAGE.CREATED_AT)
                .from(IMAGE).where(where)
                .orderBy(IMAGE.CREATED_AT.desc(), IMAGE.ID.desc())
                .limit(PAGE).offset((at - 1) * PAGE)
                .fetch();
        List<Long> ids = rows.map(r -> r.value1());
        Map<Long, StoredImage> stored = images.findAll(ids);
        Map<Long, List<Use>> uses = uses(ids);
        List<Picture> items = new ArrayList<>();
        for (var r : rows) {
            StoredImage image = stored.get(r.value1());
            if (image == null) {
                continue;
            }
            items.add(new Picture(r.value1(), image.url(640), image.url(Integer.MAX_VALUE), r.value2(), r.value3(), r.value4(),
                    r.value5() != null, r.value6(), r.value7(), r.value8(), r.value9(), uses.getOrDefault(r.value1(), List.of())));
        }
        return new Page(items, total, at);
    }

    /** Takes a picture out of the library; chapters that show it keep showing it. */
    void remove(long accountId, long imageId) {
        int changed = db.update(IMAGE).set(IMAGE.REMOVED_AT, OffsetDateTime.now(clock))
                .where(IMAGE.ID.eq(imageId).and(mine(accountId))).execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Такої картинки у вас немає.");
        }
    }

    /** Where each picture would go in the edition, in chapter and paragraph order; nothing is saved. */
    List<Place> plan(long accountId, long editionId, Collection<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            throw UserFacingException.badRequest("Виберіть картинки.");
        }
        if (imageIds.size() > 500) {
            throw UserFacingException.badRequest("За раз — до 500 картинок.");
        }
        var rows = db.select(IMAGE.ID, IMAGE.FILE_NAME).from(IMAGE)
                .where(IMAGE.ID.in(imageIds).and(mine(accountId))).fetch();
        Map<Long, StoredImage> stored = images.findAll(rows.map(r -> r.value1()));
        Map<Integer, EditorModels.CurrentText> texts = new LinkedHashMap<>();
        List<Place> places = new ArrayList<>();
        for (var r : rows) {
            long id = r.value1();
            String name = r.value2();
            String url = stored.containsKey(id) ? stored.get(id).url(640) : null;
            int[] spot = spot(name);
            if (spot == null) {
                places.add(new Place(id, name, url, null, null, null,
                        "З назви файлу не видно, куди її ставити: назвіть файл «27-5.png» — глава 27, після 5-го абзацу."));
                continue;
            }
            int number = spot[0];
            int paragraph = spot[1];
            EditorModels.CurrentText text = texts.computeIfAbsent(number, n -> current(editionId, n));
            if (text == null) {
                places.add(new Place(id, name, url, number, paragraph, null, "Глави %d немає або її ще не опубліковано.".formatted(number)));
                continue;
            }
            List<Block> paragraphs = text.blocks().stream().filter(PictureLibrary::paragraph).toList();
            if (paragraph > paragraphs.size()) {
                places.add(new Place(id, name, url, number, paragraph, null,
                        "У главі %d лише %d абзаців.".formatted(number, paragraphs.size())));
                continue;
            }
            if (BlockRules.images(text.blocks()).contains(id)) {
                places.add(new Place(id, name, url, number, paragraph, null, "Уже стоїть у цій главі."));
                continue;
            }
            String quote = paragraph == 0 ? "на самому початку глави" : quote(paragraphs.get(paragraph - 1).text());
            places.add(new Place(id, name, url, number, paragraph, quote, null));
        }
        places.sort(Comparator.comparing((Place place) -> place.chapter() == null ? Integer.MAX_VALUE : place.chapter())
                .thenComparing(place -> place.paragraph() == null ? 0 : place.paragraph())
                .thenComparing(place -> place.fileName() == null ? "" : place.fileName()));
        return places;
    }

    /**
     * Puts the pictures into their chapters: one new revision per chapter, as if the person
     * inserted them in the editor and pressed «Опублікувати».
     */
    Placed place(long accountId, long editionId, Collection<Long> imageIds) {
        List<Place> places = plan(accountId, editionId, imageIds);
        List<String> skipped = new ArrayList<>();
        Map<Integer, List<Place>> byChapter = new LinkedHashMap<>();
        for (Place place : places) {
            if (place.problem() != null) {
                skipped.add(label(place) + ": " + place.problem());
            } else {
                byChapter.computeIfAbsent(place.chapter(), n -> new ArrayList<>()).add(place);
            }
        }
        int chaptersDone = 0;
        int pictures = 0;
        for (Map.Entry<Integer, List<Place>> entry : byChapter.entrySet()) {
            EditorModels.CurrentText text = current(editionId, entry.getKey());
            if (text == null) {
                entry.getValue().forEach(place -> skipped.add(label(place) + ": глави вже немає."));
                continue;
            }
            List<Block> blocks = insert(text.blocks(), entry.getValue());
            try {
                chapters.publish(editionId, entry.getKey(), accountId, text.title(), blocks, text.revisionId(), true);
                chaptersDone++;
                pictures += entry.getValue().size();
            } catch (UserFacingException failure) {
                entry.getValue().forEach(place -> skipped.add(label(place) + ": " + failure.getMessage()));
            }
        }
        return new Placed(chaptersDone, pictures, skipped);
    }

    /** Each picture right after its paragraph; several after one paragraph keep the file order. */
    static List<Block> insert(List<Block> blocks, List<Place> places) {
        Map<Integer, List<Place>> after = places.stream()
                .collect(Collectors.groupingBy(Place::paragraph, LinkedHashMap::new, Collectors.toList()));
        List<Block> out = new ArrayList<>(blocks.size() + places.size());
        after.getOrDefault(0, List.of()).forEach(place -> out.add(picture(place)));
        int seen = 0;
        for (Block block : blocks) {
            out.add(block);
            if (paragraph(block)) {
                seen++;
                after.getOrDefault(seen, List.of()).forEach(place -> out.add(picture(place)));
            }
        }
        return out;
    }

    /** Chapter and paragraph from a file name, or null when the name says nothing. */
    static int[] spot(String fileName) {
        if (fileName == null) {
            return null;
        }
        Matcher match = PLACE.matcher(fileName.strip());
        if (!match.find()) {
            return null;
        }
        return new int[] {Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))};
    }

    private static boolean paragraph(Block block) {
        return "paragraph".equals(block.type()) && !block.text().isBlank();
    }

    private static Block picture(Place place) {
        return new Block(null, "image", List.of(), place.imageId(), null);
    }

    private static String quote(String text) {
        String clean = text.strip().replaceAll("\\s+", " ");
        return clean.length() <= 90 ? clean : clean.substring(0, 90).strip() + "…";
    }

    private static String label(Place place) {
        return place.fileName() == null ? "Картинка " + place.imageId() : place.fileName();
    }

    private EditorModels.CurrentText current(long editionId, int number) {
        try {
            return chapters.current(editionId, number);
        } catch (UserFacingException missing) {
            return null;
        }
    }

    private static Condition mine(long accountId) {
        return IMAGE.OWNER_ACCOUNT_ID.eq(accountId).and(IMAGE.KIND.eq("illustration"))
                .and(IMAGE.REMOVED_AT.isNull()).and(IMAGE.HIDDEN_AT.isNull());
    }

    /** Ids of pictures in any published chapter. */
    private static org.jooq.Select<org.jooq.Record1<Long>> usedPictures() {
        Field<Long> id = DSL.field("(b->>'imageId')::bigint", SQLDataType.BIGINT);
        return DSL.select(id)
                .from(CHAPTER.join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID)),
                        DSL.table("jsonb_array_elements({0}) b", REVISION.BLOCKS))
                .where(DSL.condition("b->>'type' = 'image' and b->>'imageId' is not null"));
    }

    /** Published chapters showing each picture, earliest first. */
    private Map<Long, List<Use>> uses(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Field<Long> id = DSL.field("(b->>'imageId')::bigint", SQLDataType.BIGINT);
        Set<String> wanted = ids.stream().map(String::valueOf).collect(Collectors.toSet());
        return db.select(id, CHAPTER.EDITION_ID, NOVEL.SLUG, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), CHAPTER.NUMBER)
                .from(CHAPTER.join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                        .join(EDITION).on(EDITION.ID.eq(CHAPTER.EDITION_ID))
                        .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)),
                        DSL.table("jsonb_array_elements({0}) b", REVISION.BLOCKS))
                .where(DSL.condition("b->>'type' = 'image'").and(DSL.field("b->>'imageId'", String.class).in(wanted)))
                .orderBy(CHAPTER.EDITION_ID, CHAPTER.NUMBER)
                .fetchGroups(r -> r.value1(), r -> new Use(r.value2(), r.value3(), r.value4(), r.value5()));
    }
}
