package space.panrid.novelka.reading.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CHAPTER_READ;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_RATING;
import static space.panrid.novelka.jooq.Tables.EDITION_SUBSCRIPTION;
import static space.panrid.novelka.jooq.Tables.LIBRARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.NOVEL_SLUG_ALIAS;
import static space.panrid.novelka.jooq.Tables.NOVEL_TAG;
import static space.panrid.novelka.jooq.Tables.READING_PROGRESS;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.SUGGESTION;
import static space.panrid.novelka.jooq.Tables.TAG;
import static space.panrid.novelka.jooq.Tables.TAKEOVER_REQUEST;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;
import static space.panrid.novelka.jooq.Tables.VOLUME;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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

import space.panrid.novelka.catalog.NovelFacts;
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
                EDITION.ADULT, EDITION.CHAPTER_COUNT, EDITION.LAST_PUBLISHED_AT, NOVEL.SOURCE_CHAPTER_COUNT));
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
                    tags.getOrDefault(r.get(NOVEL.ID), List.of()), r.get(EDITION.LAST_PUBLISHED_AT), r.get(NOVEL.SOURCE_CHAPTER_COUNT));
        }).toList();
    }

    Map<Long, List<String>> tagsOf(Collection<Long> novelIds, int limit) {
        if (novelIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        db.select(NOVEL_TAG.NOVEL_ID, TAG.NAME).from(NOVEL_TAG).join(TAG).on(TAG.ID.eq(NOVEL_TAG.TAG_ID))
                .where(NOVEL_TAG.NOVEL_ID.in(novelIds))
                .orderBy(NOVEL_TAG.NOVEL_ID, TAG.POSITION.asc().nullsLast(), TAG.NAME)
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
                int first = range.get(0, Integer.class);
                int last = range.get(1, Integer.class);
                Map<Integer, String> labels = new HashMap<>();
                db.select(CHAPTER.NUMBER, CHAPTER.LABEL).from(CHAPTER)
                        .where(CHAPTER.EDITION_ID.eq(card.editionId()), CHAPTER.NUMBER.in(first, last))
                        .forEach(r -> labels.put(r.value1(), r.value2()));
                result.add(new Views.NewChapters(card, first, last, at, labels.get(first), labels.get(last)));
            }
        }
        return result;
    }

    Optional<Long> accountByNick(String nick) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.eq(nick.strip().toLowerCase(java.util.Locale.ROOT)))
                .fetchOptional(ACCOUNT.ID);
    }

    /** What a person works on: translations and works of the teams they own or belong to. */
    List<Card> worksOf(long accountId, boolean adult) {
        var member = DSL.select(TEAM_MEMBER.TEAM_ID).from(TEAM_MEMBER).where(TEAM_MEMBER.ACCOUNT_ID.eq(accountId));
        return toCards(cards().where(visible(adult).and(TEAM.OWNER_ID.eq(accountId).or(TEAM.ID.in(member))))
                .orderBy(DSL.coalesce(EDITION.LAST_PUBLISHED_AT, EDITION.CREATED_AT).desc()).limit(50).fetch());
    }

    /** What a person reads now, for their profile — only when they let others see it. */
    Views.Activity activityOf(long accountId, boolean adult) {
        boolean shown = Boolean.TRUE.equals(db.select(ACCOUNT.SHOW_READING).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOne(ACCOUNT.SHOW_READING));
        List<Card> reading = !shown ? List.of() : toCards(cards().join(LIBRARY_ENTRY).on(LIBRARY_ENTRY.EDITION_ID.eq(EDITION.ID))
                .where(LIBRARY_ENTRY.ACCOUNT_ID.eq(accountId).and(LIBRARY_ENTRY.LIST.eq("reading")).and(visible(adult)))
                .orderBy(LIBRARY_ENTRY.UPDATED_AT.desc()).limit(12).fetch());
        int accepted = db.fetchCount(SUGGESTION, SUGGESTION.AUTHOR_ID.eq(accountId).and(SUGGESTION.STATE.eq("accepted")));
        return new Views.Activity(reading, accepted);
    }

    List<Views.ContinueItem> continueReading(long accountId, boolean adult, int limit) {
        List<Record> rows = cards(READING_PROGRESS.CHAPTER_NUMBER, READING_PROGRESS.POSITION, CHAPTER.LABEL)
                .join(READING_PROGRESS).on(READING_PROGRESS.EDITION_ID.eq(EDITION.ID))
                .leftJoin(CHAPTER).on(CHAPTER.EDITION_ID.eq(EDITION.ID), CHAPTER.NUMBER.eq(READING_PROGRESS.CHAPTER_NUMBER))
                .where(READING_PROGRESS.ACCOUNT_ID.eq(accountId).and(visible(adult)))
                .orderBy(READING_PROGRESS.UPDATED_AT.desc())
                .limit(limit)
                .fetch();
        List<Card> cards = toCards(rows);
        List<Views.ContinueItem> result = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Place place = resumeAt(rows.get(i).get(EDITION.ID), rows.get(i).get(READING_PROGRESS.CHAPTER_NUMBER),
                    rows.get(i).get(READING_PROGRESS.POSITION), rows.get(i).get(CHAPTER.LABEL));
            result.add(new Views.ContinueItem(cards.get(i), place.number(), place.position(), place.label()));
        }
        return result;
    }

    /** Where «Продовжити» leads: the place, or the start of the next chapter once the place is read to its end. */
    private record Place(int number, float position, String label) {
    }

    private Place resumeAt(long editionId, int number, float position, String label) {
        if (position < LibraryService.FINISHED) {
            return new Place(number, position, label);
        }
        Record next = db.select(CHAPTER.NUMBER, CHAPTER.LABEL).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.gt(number), CHAPTER.PUBLISHED_REVISION_ID.isNotNull())
                .orderBy(CHAPTER.NUMBER).limit(1).fetchOne();
        return next == null ? new Place(number, position, label) : new Place(next.get(CHAPTER.NUMBER), 0f, next.get(CHAPTER.LABEL));
    }

    /**
     * The catalog: every novel with chapters, or those the words and tags lead to. Words are looked
     * for in the titles, the author and the tags' names, so «магія» finds novels tagged «Магія».
     *
     * @param sort popular, updated, new, title, or relevance — titles beginning with the words first
     */
    Views.Found<Card> search(String query, List<String> tagSlugs, String kind, String machine, String sort,
            boolean adult, int page, int size) {
        Condition where = visible(adult).and(EDITION.CHAPTER_COUNT.gt(0));
        String words = query == null ? "" : query.strip();
        if (!words.isEmpty()) {
            String escaped = escapeLike(words);
            String like = "%" + escaped + "%";
            where = where.and(NOVEL.TITLE.likeIgnoreCase(like).or(EDITION.TITLE.likeIgnoreCase(like))
                    .or(NOVEL.AUTHOR.likeIgnoreCase(like))
                    // The novel's other names: English, original, and any others readers know it by.
                    .or(NOVEL.TITLE_ENGLISH.likeIgnoreCase(like)).or(NOVEL.TITLE_ORIGINAL.likeIgnoreCase(like))
                    .or(DSL.condition("array_to_string({0}, ' ') ILIKE {1}", NOVEL.ALT_TITLES, DSL.val(like)))
                    .or(DSL.exists(DSL.selectOne().from(NOVEL_TAG).join(TAG).on(TAG.ID.eq(NOVEL_TAG.TAG_ID))
                            .where(NOVEL_TAG.NOVEL_ID.eq(NOVEL.ID).and(TAG.NAME.likeIgnoreCase(like))))));
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
            case "relevance" -> List.of(DSL.when(DSL.coalesce(EDITION.TITLE, NOVEL.TITLE).likeIgnoreCase(escapeLike(words) + "%"), 0)
                    .when(DSL.coalesce(EDITION.TITLE, NOVEL.TITLE).likeIgnoreCase("%" + escapeLike(words) + "%"), 1)
                    .when(NOVEL.TITLE_ENGLISH.likeIgnoreCase(escapeLike(words) + "%"), 1)
                    .otherwise(2).asc(),
                    popularity().desc());
            default -> List.of(popularity().desc(), EDITION.LAST_PUBLISHED_AT.desc().nullsLast());
        };
        List<SortField<?>> stable = new ArrayList<>(order);
        stable.add(EDITION.ID.asc());
        List<Record> rows = cards().where(where).orderBy(stable).limit(size + 1).offset((page - 1) * size).fetch();
        boolean more = rows.size() > size;
        int total = page == 1 && !more ? rows.size() : db.fetchCount(cards().where(where));
        return new Views.Found<>(toCards(more ? rows.subList(0, size) : rows), page, more, total);
    }

    private static String escapeLike(String words) {
        return words.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Tags whose names hold the words, those on more novels first. */
    List<Views.TagCount> tagsLike(String words, boolean adult, int limit) {
        Field<Integer> novels = DSL.countDistinct(NOVEL_TAG.NOVEL_ID).as("novels");
        String escaped = escapeLike(words.strip());
        return db.select(TAG.NAME, TAG.SLUG, novels).from(TAG)
                .join(NOVEL_TAG).on(NOVEL_TAG.TAG_ID.eq(TAG.ID))
                .join(EDITION).on(EDITION.NOVEL_ID.eq(NOVEL_TAG.NOVEL_ID))
                .where(visible(adult).and(EDITION.CHAPTER_COUNT.gt(0)).and(TAG.NAME.likeIgnoreCase("%" + escaped + "%")))
                .groupBy(TAG.NAME, TAG.SLUG)
                .orderBy(DSL.when(TAG.NAME.likeIgnoreCase(escaped + "%"), 0).otherwise(1), novels.desc(), TAG.NAME)
                .limit(limit)
                .fetch(r -> new Views.TagCount(r.value1(), r.value2(), r.value3()));
    }

    /** The site's tag list by groups, as filters in a shop, with how many novels have each. */
    List<Views.TagGroup> tagGroups(boolean adult) {
        var counted = DSL.select(NOVEL_TAG.TAG_ID, DSL.countDistinct(NOVEL_TAG.NOVEL_ID).as("novels")).from(NOVEL_TAG)
                .join(EDITION).on(EDITION.NOVEL_ID.eq(NOVEL_TAG.NOVEL_ID))
                .where(visible(adult).and(EDITION.CHAPTER_COUNT.gt(0)))
                .groupBy(NOVEL_TAG.TAG_ID).asTable("counted");
        Field<Integer> novels = DSL.coalesce(counted.field("novels", Integer.class), 0);
        Map<String, List<Views.TagCount>> groups = new LinkedHashMap<>();
        db.select(TAG.GRP, TAG.NAME, TAG.SLUG, novels).from(TAG)
                .leftJoin(counted).on(counted.field(NOVEL_TAG.TAG_ID).eq(TAG.ID))
                .where(TAG.GRP.isNotNull())
                .orderBy(TAG.POSITION)
                .forEach(r -> groups.computeIfAbsent(r.value1(), group -> new ArrayList<>())
                        .add(new Views.TagCount(r.value2(), r.value3(), r.value4())));
        return groups.entrySet().stream().map(e -> new Views.TagGroup(e.getKey(), e.getValue())).toList();
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

    /**
     * @param language the original's language (ja, en…), or null when not known (a translation entered by hand)
     * @param url      the original's page, when the site knows it
     */
    record NovelRow(long id, String slug, String title, String author, String source, JSONB description, String language, String url,
            NovelFacts facts) {
    }

    private static NovelRow novelRow(Record r) {
        return new NovelRow(r.get(NOVEL.ID), r.get(NOVEL.SLUG), r.get(NOVEL.TITLE), r.get(NOVEL.AUTHOR), r.get(NOVEL.SOURCE),
                r.get(NOVEL.DESCRIPTION), r.get(NOVEL.SOURCE_LANGUAGE), r.get(NOVEL.SOURCE_URL),
                new NovelFacts(r.get(NOVEL.TITLE_ORIGINAL), r.get(NOVEL.TITLE_ENGLISH), List.of(r.get(NOVEL.ALT_TITLES)),
                        r.get(NOVEL.SOURCE_STATUS), r.get(NOVEL.SOURCE_CHAPTER_COUNT)));
    }

    /** The novel by its address, or by an address it had before (the row then carries the current one). */
    Optional<NovelRow> novel(String slug) {
        var current = db.select(NOVEL.ID, NOVEL.SLUG, NOVEL.TITLE, NOVEL.AUTHOR, NOVEL.SOURCE, NOVEL.DESCRIPTION, NOVEL.SOURCE_LANGUAGE,
                        NOVEL.SOURCE_URL, NOVEL.TITLE_ORIGINAL, NOVEL.TITLE_ENGLISH, NOVEL.ALT_TITLES, NOVEL.SOURCE_STATUS,
                        NOVEL.SOURCE_CHAPTER_COUNT)
                .from(NOVEL).where(NOVEL.SLUG.eq(slug))
                .fetchOptional(ReadingQueries::novelRow);
        if (current.isPresent()) {
            return current;
        }
        return db.select(NOVEL.ID, NOVEL.SLUG, NOVEL.TITLE, NOVEL.AUTHOR, NOVEL.SOURCE, NOVEL.DESCRIPTION, NOVEL.SOURCE_LANGUAGE,
                        NOVEL.SOURCE_URL, NOVEL.TITLE_ORIGINAL, NOVEL.TITLE_ENGLISH, NOVEL.ALT_TITLES, NOVEL.SOURCE_STATUS,
                        NOVEL.SOURCE_CHAPTER_COUNT)
                .from(NOVEL_SLUG_ALIAS).join(NOVEL).on(NOVEL.ID.eq(NOVEL_SLUG_ALIAS.NOVEL_ID)).where(NOVEL_SLUG_ALIAS.SLUG.eq(slug))
                .fetchOptional(ReadingQueries::novelRow);
    }

    record EditionRow(long id, String teamHandle, String teamName, String title, String kind, String status,
            boolean adult, int chapterCount, Long coverImageId, JSONB description, OffsetDateTime lastPublishedAt,
            int popularity, boolean hidden, java.time.LocalDate pausedUntil, boolean downloadAllowed) {
    }

    /** All editions of a novel, the most popular first. */
    List<EditionRow> editions(long novelId) {
        Field<Integer> popularity = popularity().as("popularity");
        return db.select(EDITION.ID, TEAM.HANDLE, teamName(), EDITION.TITLE, EDITION.KIND, EDITION.STATUS,
                        EDITION.ADULT, EDITION.CHAPTER_COUNT, EDITION.COVER_IMAGE_ID, EDITION.DESCRIPTION,
                        EDITION.LAST_PUBLISHED_AT, popularity, EDITION.HIDDEN_AT, EDITION.PAUSED_UNTIL,
                        EDITION.DOWNLOAD_ALLOWED)
                .from(EDITION)
                .join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID))
                .where(EDITION.NOVEL_ID.eq(novelId))
                .orderBy(popularity.desc(), EDITION.CHAPTER_COUNT.desc(), EDITION.ID)
                .fetch(r -> new EditionRow(r.get(EDITION.ID), r.get(TEAM.HANDLE), r.get("team_name", String.class),
                        r.get(EDITION.TITLE), r.get(EDITION.KIND), r.get(EDITION.STATUS), r.get(EDITION.ADULT),
                        r.get(EDITION.CHAPTER_COUNT), r.get(EDITION.COVER_IMAGE_ID), r.get(EDITION.DESCRIPTION),
                        r.get(EDITION.LAST_PUBLISHED_AT), r.get(popularity), r.get(EDITION.HIDDEN_AT) != null,
                        r.get(EDITION.PAUSED_UNTIL), r.get(EDITION.DOWNLOAD_ALLOWED)));
    }

    List<EditionSummary> summaries(List<EditionRow> editions) {
        Map<Long, StoredImage> covers = images.findAll(editions.stream().map(EditionRow::coverImageId).toList());
        Map<Long, ? extends Record> ratings = editions.isEmpty() ? Map.<Long, Record>of()
                : db.select(EDITION_RATING.EDITION_ID, DSL.avg(EDITION_RATING.SCORE), DSL.count()).from(EDITION_RATING)
                        .where(EDITION_RATING.EDITION_ID.in(editions.stream().map(EditionRow::id).toList()))
                        .groupBy(EDITION_RATING.EDITION_ID).fetchMap(EDITION_RATING.EDITION_ID);
        return editions.stream().map(e -> summary(e, covers, ratings.get(e.id()))).toList();
    }

    private static EditionSummary summary(EditionRow e, Map<Long, StoredImage> covers, Record rating) {
        StoredImage cover = e.coverImageId() == null ? null : covers.get(e.coverImageId());
        java.math.BigDecimal average = rating == null ? null : rating.get(1, java.math.BigDecimal.class);
        return new EditionSummary(e.id(), e.teamHandle(), e.teamName(), e.kind(), e.status(), e.chapterCount(),
                cover == null ? null : cover.url(COVER_WIDTH),
                average == null ? null : average.setScale(1, java.math.RoundingMode.HALF_UP).doubleValue(),
                rating == null ? 0 : rating.get(2, Integer.class), e.pausedUntil(), e.downloadAllowed());
    }

    List<String> allTags(long novelId) {
        return tagsOf(List.of(novelId), 12).getOrDefault(novelId, List.of());
    }

    Views.Page<ChapterRow> chapters(long editionId, boolean newestFirst, int page, int size) {
        return chapters(editionId, newestFirst, page, size, null);
    }

    /** {@code reader}: marks each row read or not for that account; null for a guest. */
    Views.Page<ChapterRow> chapters(long editionId, boolean newestFirst, int page, int size, Long reader) {
        Field<Boolean> read = reader == null ? DSL.inline((Boolean) null) : DSL.field(DSL.exists(DSL.selectOne().from(CHAPTER_READ)
                .where(CHAPTER_READ.ACCOUNT_ID.eq(reader), CHAPTER_READ.EDITION_ID.eq(editionId),
                        CHAPTER_READ.CHAPTER_NUMBER.eq(CHAPTER.NUMBER))));
        List<ChapterRow> rows = db.select(CHAPTER.NUMBER, REVISION.TITLE, CHAPTER.FIRST_PUBLISHED_AT, CHAPTER.LABEL, read)
                .from(CHAPTER).join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId))
                .orderBy(newestFirst ? CHAPTER.NUMBER.desc() : CHAPTER.NUMBER.asc())
                .limit(size + 1).offset((page - 1) * size)
                .fetch(r -> new ChapterRow(r.value1(), r.value2(), r.value3(), r.value4(), null, r.value5()));
        List<Views.VolumeRef> volumes = volumes(editionId);
        rows = rows.stream().map(row -> new ChapterRow(row.number(), row.title(), row.publishedAt(), row.label(),
                volumeOf(volumes, row.number()), row.read())).toList();
        boolean more = rows.size() > size;
        return new Views.Page<>(more ? rows.subList(0, size) : rows, page, more);
    }

    record ChapterText(int number, String title, JSONB blocks, String label) {
    }

    List<Views.VolumeRef> volumes(long editionId) {
        int[] ordinary = {0};
        return db.select(VOLUME.FIRST_NUMBER, VOLUME.TITLE, VOLUME.KIND).from(VOLUME).where(VOLUME.EDITION_ID.eq(editionId))
                .orderBy(VOLUME.FIRST_NUMBER).fetch(r -> new Views.VolumeRef(r.value1(), r.value2(), r.value3(),
                        "volume".equals(r.value3()) ? ++ordinary[0] : null));
    }

    /**
     * Published chapters {@code from}..{@code to} (to: null for all after) for a downloaded book,
     * each with the volume it belongs to.
     */
    List<EpubBook.Chapter> bookChapters(long editionId, int from, Integer to) {
        List<Views.VolumeRef> volumes = volumes(editionId);
        Condition range = CHAPTER.NUMBER.ge(from);
        if (to != null) {
            range = range.and(CHAPTER.NUMBER.le(to));
        }
        return db.select(CHAPTER.NUMBER, CHAPTER.LABEL, REVISION.TITLE, REVISION.BLOCKS)
                .from(CHAPTER).join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId).and(range))
                .orderBy(CHAPTER.NUMBER)
                .fetch(r -> {
                    Views.VolumeRef volume = volumeOf(volumes, r.value1());
                    return new EpubBook.Chapter(r.value1(), r.value2(), r.value3(), json.readValue(r.value4().data(), BLOCKS),
                            volume == null ? null : volumeTitle(volume));
                });
    }

    /** «Том 2. Назва», «Пролог»: as the site shows a volume. */
    static String volumeTitle(Views.VolumeRef volume) {
        boolean titled = volume.title() != null && !volume.title().isBlank();
        if (!"volume".equals(volume.kind())) {
            return titled ? volume.title() : switch (volume.kind()) {
                case "prologue" -> "Пролог";
                case "side" -> "Побічні історії";
                default -> "Екстра";
            };
        }
        String prefix = volume.index() == null ? "Том" : "Том " + volume.index();
        return titled ? prefix + ". " + volume.title() : prefix;
    }

    /** Volumes that have published chapters, with how many: the choice of «Завантажити EPUB». */
    List<Views.VolumeChoice> volumeChoices(long editionId) {
        List<Views.VolumeRef> volumes = volumes(editionId);
        List<Integer> numbers = db.select(CHAPTER.NUMBER).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.PUBLISHED_REVISION_ID.isNotNull())
                .orderBy(CHAPTER.NUMBER).fetch(CHAPTER.NUMBER);
        List<Views.VolumeChoice> choices = new ArrayList<>();
        for (int i = 0; i < volumes.size(); i++) {
            Views.VolumeRef volume = volumes.get(i);
            int first = volume.firstNumber();
            Integer last = i + 1 < volumes.size() ? volumes.get(i + 1).firstNumber() - 1 : null;
            long count = numbers.stream().filter(n -> n >= first && (last == null || n <= last)).count();
            if (count > 0) {
                choices.add(new Views.VolumeChoice(first, last, volumeTitle(volume), (int) count));
            }
        }
        return choices;
    }

    /** The last volume starting at or before the chapter. */
    static Views.VolumeRef volumeOf(List<Views.VolumeRef> volumes, int number) {
        Views.VolumeRef found = null;
        for (Views.VolumeRef volume : volumes) {
            if (volume.firstNumber() <= number) {
                found = volume;
            }
        }
        return found;
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
        Short rating = db.select(EDITION_RATING.SCORE).from(EDITION_RATING)
                .where(EDITION_RATING.ACCOUNT_ID.eq(accountId), EDITION_RATING.EDITION_ID.eq(editionId)).fetchOne(EDITION_RATING.SCORE);
        // Chapters before the place the reader did not read: skipped, or read before marks existed.
        Record skipped = progress == null ? null : db.select(DSL.count(), DSL.min(CHAPTER.NUMBER)).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.PUBLISHED_REVISION_ID.isNotNull(),
                        CHAPTER.NUMBER.lt(progress.get(READING_PROGRESS.CHAPTER_NUMBER)),
                        DSL.notExists(DSL.selectOne().from(CHAPTER_READ).where(CHAPTER_READ.ACCOUNT_ID.eq(accountId),
                                CHAPTER_READ.EDITION_ID.eq(editionId), CHAPTER_READ.CHAPTER_NUMBER.eq(CHAPTER.NUMBER))))
                .fetchOne();
        int skippedCount = skipped == null ? 0 : skipped.get(0, Integer.class);
        Integer firstUnread = skippedCount == 0 ? null : skipped.get(1, Integer.class);
        String firstUnreadLabel = firstUnread == null ? null : db.select(CHAPTER.LABEL).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(firstUnread)).fetchOne(CHAPTER.LABEL);
        Place place = progress == null ? null : resumeAt(editionId, progress.get(READING_PROGRESS.CHAPTER_NUMBER),
                progress.get(READING_PROGRESS.POSITION), db.select(CHAPTER.LABEL).from(CHAPTER)
                        .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(progress.get(READING_PROGRESS.CHAPTER_NUMBER)))
                        .fetchOne(CHAPTER.LABEL));
        return new Views.ViewerState(list, place == null ? null : place.number(), place == null ? null : place.position(), teamRole,
                rating == null ? null : rating.intValue(), place == null ? null : place.label(),
                db.fetchExists(TAKEOVER_REQUEST, TAKEOVER_REQUEST.EDITION_ID.eq(editionId), TAKEOVER_REQUEST.STATE.eq("open"),
                        TAKEOVER_REQUEST.TEAM_ID.in(DSL.select(TEAM.ID).from(TEAM).where(TEAM.OWNER_ID.eq(accountId))
                                .union(DSL.select(TEAM_MEMBER.TEAM_ID).from(TEAM_MEMBER).where(TEAM_MEMBER.ACCOUNT_ID.eq(accountId))))),
                db.fetchExists(EDITION_SUBSCRIPTION, EDITION_SUBSCRIPTION.ACCOUNT_ID.eq(accountId),
                        EDITION_SUBSCRIPTION.EDITION_ID.eq(editionId)),
                skippedCount, firstUnread, firstUnreadLabel);
    }

    // ---- library --------------------------------------------------------------------------

    static final int LIBRARY_PAGE = 20;

    Views.LibraryPage library(long accountId, String list, boolean adult, int page) {
        int at = Math.max(1, page);
        Condition mine = LIBRARY_ENTRY.ACCOUNT_ID.eq(accountId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : LibraryService.LISTS) {
            counts.put(name, 0);
        }
        db.select(LIBRARY_ENTRY.LIST, DSL.count()).from(LIBRARY_ENTRY).join(EDITION).on(EDITION.ID.eq(LIBRARY_ENTRY.EDITION_ID))
                .where(mine.and(visible(adult))).groupBy(LIBRARY_ENTRY.LIST)
                .forEach(r -> counts.put(r.value1(), r.value2()));
        List<Record> rows = cards(READING_PROGRESS.CHAPTER_NUMBER, READING_PROGRESS.POSITION, CHAPTER.LABEL).join(LIBRARY_ENTRY).on(LIBRARY_ENTRY.EDITION_ID.eq(EDITION.ID))
                .leftJoin(READING_PROGRESS).on(READING_PROGRESS.EDITION_ID.eq(EDITION.ID).and(READING_PROGRESS.ACCOUNT_ID.eq(accountId)))
                .leftJoin(CHAPTER).on(CHAPTER.EDITION_ID.eq(EDITION.ID), CHAPTER.NUMBER.eq(READING_PROGRESS.CHAPTER_NUMBER))
                .where(mine.and(LIBRARY_ENTRY.LIST.eq(list)).and(visible(adult)))
                .orderBy(DSL.greatest(LIBRARY_ENTRY.UPDATED_AT, DSL.coalesce(READING_PROGRESS.UPDATED_AT, LIBRARY_ENTRY.UPDATED_AT)).desc(),
                        EDITION.ID)
                .limit(LIBRARY_PAGE).offset((at - 1) * LIBRARY_PAGE)
                .fetch();
        List<Card> cards = toCards(rows);
        List<Views.LibraryItem> items = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Integer number = rows.get(i).get(READING_PROGRESS.CHAPTER_NUMBER);
            Place place = number == null ? null
                    : resumeAt(rows.get(i).get(EDITION.ID), number, rows.get(i).get(READING_PROGRESS.POSITION), rows.get(i).get(CHAPTER.LABEL));
            items.add(new Views.LibraryItem(cards.get(i), list, place == null ? null : place.number(), place == null ? null : place.label()));
        }
        int total = counts.getOrDefault(list, 0);
        return new Views.LibraryPage(items, counts, total, at, at * LIBRARY_PAGE < total);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
