package space.panrid.novelka.reading.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.LIBRARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.NOVEL_TAG;
import static space.panrid.novelka.jooq.Tables.READING_PROGRESS;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.TAG;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.reading.internal.Views.Card;
import space.panrid.novelka.reading.internal.Views.ChapterRow;
import space.panrid.novelka.reading.internal.Views.EditionSummary;
import space.panrid.novelka.reading.internal.Views.ReaderBlock;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read side of the reading pages. Joins catalog, text and team tables directly with jOOQ;
 * writes to them go only through the owning module's service (architecture.md).
 */
@Component
class ReadingQueries {

    static final int COVER_WIDTH = 480;
    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final Images images;
    private final JsonMapper json;
    private final Clock clock;

    ReadingQueries(DSLContext db, Images images, JsonMapper json, Clock clock) {
        this.db = db;
        this.images = images;
        this.json = json;
        this.clock = clock;
    }

    /** Editions a viewer may see: not hidden, and 18+ only after confirming the age. */
    static Condition visible(boolean adultConfirmed) {
        Condition condition = EDITION.HIDDEN_AT.isNull();
        return adultConfirmed ? condition : condition.and(EDITION.ADULT.isFalse());
    }

    /** Readers in the last 30 days count twice as much as library entries. */
    Field<Integer> popularity() {
        OffsetDateTime monthAgo = now().minusDays(30);
        Field<Integer> readers = DSL.select(DSL.countDistinct(READING_PROGRESS.ACCOUNT_ID)).from(READING_PROGRESS)
                .where(READING_PROGRESS.EDITION_ID.eq(EDITION.ID).and(READING_PROGRESS.UPDATED_AT.gt(monthAgo)))
                .asField();
        Field<Integer> shelved = DSL.select(DSL.count()).from(LIBRARY_ENTRY)
                .where(LIBRARY_ENTRY.EDITION_ID.eq(EDITION.ID).and(LIBRARY_ENTRY.LIST.in("reading", "planned", "done")))
                .asField();
        return readers.mul(2).plus(shelved);
    }

    // ---- lists of cards -------------------------------------------------------------------

    private Field<String> teamName() {
        return DSL.coalesce(TEAM.NAME, ACCOUNT.NICK).as("team_name");
    }

    private Field<String> title() {
        return DSL.coalesce(EDITION.TITLE, NOVEL.TITLE).as("title");
    }

    /** Card columns; {@code extra} adds columns of tables the caller joins itself. */
    private SelectJoinStep<Record> cards(SelectField<?>... extra) {
        List<SelectField<?>> fields = new ArrayList<>(List.of(EDITION.ID, NOVEL.ID, NOVEL.SLUG, TEAM.HANDLE,
                teamName(), title(), NOVEL.AUTHOR, EDITION.COVER_IMAGE_ID, EDITION.KIND, EDITION.STATUS,
                EDITION.ADULT, EDITION.CHAPTER_COUNT, EDITION.LAST_PUBLISHED_AT));
        fields.addAll(List.of(extra));
        return db.select(fields)
                .from(EDITION)
                .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID));
    }

    /** Turns card rows into cards: covers and first tags are fetched in two extra queries, not per row. */
    private List<Card> toCards(List<? extends Record> rows) {
        Map<Long, StoredImage> covers = images.findAll(rows.stream().map(r -> r.get(EDITION.COVER_IMAGE_ID)).toList());
        Map<Long, List<String>> tags = tagsOf(rows.stream().map(r -> r.get(NOVEL.ID)).toList(), 3);
        return rows.stream().map(r -> {
            StoredImage cover = r.get(EDITION.COVER_IMAGE_ID) == null ? null : covers.get(r.get(EDITION.COVER_IMAGE_ID));
            return new Card(r.get(EDITION.ID), r.get(NOVEL.SLUG), r.get(TEAM.HANDLE), r.get("team_name", String.class),
                    r.get("title", String.class), r.get(NOVEL.AUTHOR), cover == null ? null : cover.url(COVER_WIDTH),
                    r.get(EDITION.KIND), r.get(EDITION.STATUS), r.get(EDITION.ADULT), r.get(EDITION.CHAPTER_COUNT),
                    tags.getOrDefault(r.get(NOVEL.ID), List.of()), r.get(EDITION.LAST_PUBLISHED_AT));
        }).toList();
    }

    Map<Long, List<String>> tagsOf(Collection<Long> novelIds, int limit) {
        if (novelIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        db.select(NOVEL_TAG.NOVEL_ID, TAG.NAME).from(NOVEL_TAG).join(TAG).on(TAG.ID.eq(NOVEL_TAG.TAG_ID))
                .where(NOVEL_TAG.NOVEL_ID.in(novelIds))
                .orderBy(NOVEL_TAG.NOVEL_ID, TAG.NAME)
                .forEach(r -> {
                    List<String> names = result.computeIfAbsent(r.value1(), id -> new ArrayList<>());
                    if (names.size() < limit) {
                        names.add(r.value2());
                    }
                });
        return result;
    }

    List<Card> popular(boolean adult, int limit) {
        return toCards(cards().where(visible(adult).and(EDITION.CHAPTER_COUNT.gt(0)))
                .orderBy(popularity().desc(), EDITION.LAST_PUBLISHED_AT.desc().nullsLast(), EDITION.ID)
                .limit(limit).fetch());
    }

    /** Recently published editions, each with the range of chapters of its latest batch. */
    List<Views.NewChapters> newChapters(boolean adult, int limit) {
        List<Record> rows = cards().where(visible(adult).and(EDITION.LAST_PUBLISHED_AT.isNotNull()))
                .orderBy(EDITION.LAST_PUBLISHED_AT.desc(), EDITION.ID.desc()).limit(limit).fetch();
        List<Card> cards = toCards(rows);
        List<Views.NewChapters> result = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            OffsetDateTime at = rows.get(i).get(EDITION.LAST_PUBLISHED_AT);
            // Chapters published within the hour before the last one count as one release.
            Record range = db.select(DSL.min(CHAPTER.NUMBER), DSL.max(CHAPTER.NUMBER)).from(CHAPTER)
                    .where(CHAPTER.EDITION_ID.eq(card.editionId())
                            .and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull())
                            .and(CHAPTER.FIRST_PUBLISHED_AT.ge(at.minusHours(1))))
                    .fetchOne();
            if (range != null && range.get(0) != null) {
                result.add(new Views.NewChapters(card, range.get(0, Integer.class), range.get(1, Integer.class), at));
            }
        }
        return result;
    }

    List<Views.ContinueItem> continueReading(long accountId, boolean adult, int limit) {
        List<Record> rows = cards(READING_PROGRESS.CHAPTER_NUMBER, READING_PROGRESS.POSITION)
                .join(READING_PROGRESS).on(READING_PROGRESS.EDITION_ID.eq(EDITION.ID))
                .where(READING_PROGRESS.ACCOUNT_ID.eq(accountId).and(visible(adult)))
                .orderBy(READING_PROGRESS.UPDATED_AT.desc())
                .limit(limit)
                .fetch();
        List<Card> cards = toCards(rows);
        List<Views.ContinueItem> result = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            result.add(new Views.ContinueItem(cards.get(i), rows.get(i).get(READING_PROGRESS.CHAPTER_NUMBER),
                    rows.get(i).get(READING_PROGRESS.POSITION)));
        }
        return result;
    }

    Views.Page<Card> search(String query, List<String> tagSlugs, String kind, String machine, String sort,
            boolean adult, int page, int size) {
        Condition where = visible(adult).and(EDITION.CHAPTER_COUNT.gt(0));
        if (query != null && !query.isBlank()) {
            String like = "%" + query.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where = where.and(NOVEL.TITLE.likeIgnoreCase(like).or(EDITION.TITLE.likeIgnoreCase(like))
                    .or(NOVEL.AUTHOR.likeIgnoreCase(like)));
        }
        for (String slug : tagSlugs) {
            where = where.and(DSL.exists(DSL.selectOne().from(NOVEL_TAG).join(TAG).on(TAG.ID.eq(NOVEL_TAG.TAG_ID))
                    .where(NOVEL_TAG.NOVEL_ID.eq(NOVEL.ID).and(TAG.SLUG.eq(slug.toLowerCase(Locale.ROOT))))));
        }
        if ("original".equals(kind)) {
            where = where.and(EDITION.KIND.eq("original"));
        } else if ("translation".equals(kind)) {
            where = where.and(EDITION.KIND.ne("original"));
        }
        if ("human".equals(machine)) {
            where = where.and(EDITION.KIND.in("human", "original"));
        } else if ("machine".equals(machine)) {
            where = where.and(EDITION.KIND.in("machine", "mixed"));
        }
        List<SortField<?>> order = switch (sort == null ? "popular" : sort) {
            case "updated" -> List.of(EDITION.LAST_PUBLISHED_AT.desc().nullsLast());
            case "new" -> List.of(EDITION.CREATED_AT.desc());
            case "title" -> List.of(DSL.coalesce(EDITION.TITLE, NOVEL.TITLE).asc());
            default -> List.of(popularity().desc(), EDITION.LAST_PUBLISHED_AT.desc().nullsLast());
        };
        List<SortField<?>> stable = new ArrayList<>(order);
        stable.add(EDITION.ID.asc());
        List<Record> rows = cards().where(where).orderBy(stable).limit(size + 1).offset((page - 1) * size).fetch();
        boolean more = rows.size() > size;
        return new Views.Page<>(toCards(more ? rows.subList(0, size) : rows), page, more);
    }

    List<Views.TagCount> tags(boolean adult, int limit) {
        Field<Integer> novels = DSL.countDistinct(NOVEL_TAG.NOVEL_ID).as("novels");
        return db.select(TAG.NAME, TAG.SLUG, novels).from(TAG)
                .join(NOVEL_TAG).on(NOVEL_TAG.TAG_ID.eq(TAG.ID))
                .join(EDITION).on(EDITION.NOVEL_ID.eq(NOVEL_TAG.NOVEL_ID))
                .where(visible(adult).and(EDITION.CHAPTER_COUNT.gt(0)))
                .groupBy(TAG.NAME, TAG.SLUG)
                .orderBy(novels.desc(), TAG.NAME)
                .limit(limit)
                .fetch(r -> new Views.TagCount(r.value1(), r.value2(), r.value3()));
    }

    // ---- one novel ------------------------------------------------------------------------

    record NovelRow(long id, String slug, String title, String author, String source, JSONB description) {
    }

    Optional<NovelRow> novel(String slug) {
        return db.select(NOVEL.ID, NOVEL.SLUG, NOVEL.TITLE, NOVEL.AUTHOR, NOVEL.SOURCE, NOVEL.DESCRIPTION)
                .from(NOVEL).where(NOVEL.SLUG.eq(slug))
                .fetchOptional(r -> new NovelRow(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6()));
    }

    record EditionRow(long id, String teamHandle, String teamName, String title, String kind, String status,
            boolean adult, int chapterCount, Long coverImageId, JSONB description, OffsetDateTime lastPublishedAt,
            int popularity, boolean hidden) {
    }

    /** All editions of a novel, the most popular first. */
    List<EditionRow> editions(long novelId) {
        Field<Integer> popularity = popularity().as("popularity");
        return db.select(EDITION.ID, TEAM.HANDLE, teamName(), EDITION.TITLE, EDITION.KIND, EDITION.STATUS,
                        EDITION.ADULT, EDITION.CHAPTER_COUNT, EDITION.COVER_IMAGE_ID, EDITION.DESCRIPTION,
                        EDITION.LAST_PUBLISHED_AT, popularity, EDITION.HIDDEN_AT)
                .from(EDITION)
                .join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID))
                .where(EDITION.NOVEL_ID.eq(novelId))
                .orderBy(popularity.desc(), EDITION.CHAPTER_COUNT.desc(), EDITION.ID)
                .fetch(r -> new EditionRow(r.get(EDITION.ID), r.get(TEAM.HANDLE), r.get("team_name", String.class),
                        r.get(EDITION.TITLE), r.get(EDITION.KIND), r.get(EDITION.STATUS), r.get(EDITION.ADULT),
                        r.get(EDITION.CHAPTER_COUNT), r.get(EDITION.COVER_IMAGE_ID), r.get(EDITION.DESCRIPTION),
                        r.get(EDITION.LAST_PUBLISHED_AT), r.get(popularity), r.get(EDITION.HIDDEN_AT) != null));
    }

    List<EditionSummary> summaries(List<EditionRow> editions) {
        Map<Long, StoredImage> covers = images.findAll(editions.stream().map(EditionRow::coverImageId).toList());
        return editions.stream().map(e -> summary(e, covers)).toList();
    }

    private static EditionSummary summary(EditionRow e, Map<Long, StoredImage> covers) {
        StoredImage cover = e.coverImageId() == null ? null : covers.get(e.coverImageId());
        return new EditionSummary(e.id(), e.teamHandle(), e.teamName(), e.kind(), e.status(), e.chapterCount(),
                cover == null ? null : cover.url(COVER_WIDTH));
    }

    List<String> allTags(long novelId) {
        return tagsOf(List.of(novelId), 12).getOrDefault(novelId, List.of());
    }

    Views.Page<ChapterRow> chapters(long editionId, boolean newestFirst, int page, int size) {
        List<ChapterRow> rows = db.select(CHAPTER.NUMBER, REVISION.TITLE, CHAPTER.FIRST_PUBLISHED_AT, CHAPTER.LABEL)
                .from(CHAPTER).join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId))
                .orderBy(newestFirst ? CHAPTER.NUMBER.desc() : CHAPTER.NUMBER.asc())
                .limit(size + 1).offset((page - 1) * size)
                .fetch(r -> new ChapterRow(r.value1(), r.value2(), r.value3(), r.value4()));
        boolean more = rows.size() > size;
        return new Views.Page<>(more ? rows.subList(0, size) : rows, page, more);
    }

    record ChapterText(int number, String title, JSONB blocks, String label) {
    }

    Optional<ChapterText> chapter(long editionId, int number) {
        return db.select(CHAPTER.NUMBER, REVISION.TITLE, REVISION.BLOCKS, CHAPTER.LABEL)
                .from(CHAPTER).join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.NUMBER.eq(number)))
                .fetchOptional(r -> new ChapterText(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    /** Numbers of the published chapters around {@code number}; gaps in numbering are skipped. */
    Integer neighbour(long editionId, int number, boolean next) {
        Condition published = CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull());
        return next
                ? db.select(DSL.min(CHAPTER.NUMBER)).from(CHAPTER).where(published.and(CHAPTER.NUMBER.gt(number))).fetchOne(0, Integer.class)
                : db.select(DSL.max(CHAPTER.NUMBER)).from(CHAPTER).where(published.and(CHAPTER.NUMBER.lt(number))).fetchOne(0, Integer.class);
    }

    Integer firstChapter(long editionId) {
        return neighbour(editionId, 0, true);
    }

    /** Blocks as the reader draws them: picture ids become URLs. */
    List<ReaderBlock> readerBlocks(JSONB stored) {
        if (stored == null) {
            return List.of();
        }
        List<Block> blocks = json.readValue(stored.data(), BLOCKS);
        Map<Long, StoredImage> pictures = images.findAll(blocks.stream().map(Block::imageId).toList());
        return blocks.stream()
                .filter(block -> !block.type().equals("image") || pictures.containsKey(block.imageId()))
                .map(block -> new ReaderBlock(block.id(), block.type(), block.content(),
                        block.imageId() == null ? null : pictures.get(block.imageId()).url(1280)))
                .collect(Collectors.toList());
    }

    Views.ViewerState viewer(long accountId, long editionId) {
        String list = db.select(LIBRARY_ENTRY.LIST).from(LIBRARY_ENTRY)
                .where(LIBRARY_ENTRY.ACCOUNT_ID.eq(accountId).and(LIBRARY_ENTRY.EDITION_ID.eq(editionId)))
                .fetchOne(LIBRARY_ENTRY.LIST);
        Record progress = db.select(READING_PROGRESS.CHAPTER_NUMBER, READING_PROGRESS.POSITION).from(READING_PROGRESS)
                .where(READING_PROGRESS.ACCOUNT_ID.eq(accountId).and(READING_PROGRESS.EDITION_ID.eq(editionId)))
                .fetchOne();
        String teamRole = db.select(DSL.when(TEAM.OWNER_ID.eq(accountId), "owner").otherwise(TEAM_MEMBER.ROLE))
                .from(EDITION).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .leftJoin(TEAM_MEMBER).on(TEAM_MEMBER.TEAM_ID.eq(TEAM.ID).and(TEAM_MEMBER.ACCOUNT_ID.eq(accountId)))
                .where(EDITION.ID.eq(editionId))
                .fetchOne(0, String.class);
        return new Views.ViewerState(list, progress == null ? null : progress.get(READING_PROGRESS.CHAPTER_NUMBER),
                progress == null ? null : progress.get(READING_PROGRESS.POSITION), teamRole);
    }

    // ---- library --------------------------------------------------------------------------

    Views.LibraryPage library(long accountId, String list, boolean adult) {
        Condition mine = LIBRARY_ENTRY.ACCOUNT_ID.eq(accountId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : LibraryService.LISTS) {
            counts.put(name, 0);
        }
        db.select(LIBRARY_ENTRY.LIST, DSL.count()).from(LIBRARY_ENTRY).join(EDITION).on(EDITION.ID.eq(LIBRARY_ENTRY.EDITION_ID))
                .where(mine.and(visible(adult))).groupBy(LIBRARY_ENTRY.LIST)
                .forEach(r -> counts.put(r.value1(), r.value2()));
        List<Record> rows = cards(READING_PROGRESS.CHAPTER_NUMBER).join(LIBRARY_ENTRY).on(LIBRARY_ENTRY.EDITION_ID.eq(EDITION.ID))
                .leftJoin(READING_PROGRESS).on(READING_PROGRESS.EDITION_ID.eq(EDITION.ID).and(READING_PROGRESS.ACCOUNT_ID.eq(accountId)))
                .where(mine.and(LIBRARY_ENTRY.LIST.eq(list)).and(visible(adult)))
                .orderBy(DSL.greatest(LIBRARY_ENTRY.UPDATED_AT, DSL.coalesce(READING_PROGRESS.UPDATED_AT, LIBRARY_ENTRY.UPDATED_AT)).desc())
                .fetch();
        List<Card> cards = toCards(rows);
        List<Views.LibraryItem> items = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            items.add(new Views.LibraryItem(cards.get(i), list, rows.get(i).get(READING_PROGRESS.CHAPTER_NUMBER)));
        }
        return new Views.LibraryPage(items, counts);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
