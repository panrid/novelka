package space.panrid.novelka.reading.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.COMMENT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_STAT;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.READING_PROGRESS;
import static space.panrid.novelka.jooq.Tables.READING_STAT;
import static space.panrid.novelka.support.Browser.json;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import space.panrid.novelka.text.Chapters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Етап 21.5: long lists read by key, and counters counted ahead stay right. */
@IntegrationTest
class KeysetAndCountersTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    ReadingQueries queries;

    @Autowired
    EditionStats stats;

    @Autowired
    Chapters chapters;

    Person admin;
    long adminId;
    String word;

    @BeforeEach
    void anAdministratorImportsNovels() {
        admin = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "admin").where(ACCOUNT.NICK.eq(admin.nick())).execute();
        adminId = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(admin.nick())).fetchSingle(ACCOUNT.ID);
        word = "к" + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void theChaptersOfANovelGoByKeyAcrossPages() {
        String slug = importNovel("Довга " + word, 45);
        long editionId = editionOf(slug);
        Browser guest = new Browser(port);
        String list = "/api/novels/" + slug + "/chapters";

        JsonNode first = read(guest.get(list));
        assertThat(numbers(first)).containsExactlyElementsOf(range(1, 20));
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        assertThat(first.path("next").asString()).isEqualTo("20");

        JsonNode second = read(guest.get(list + "?after=20"));
        assertThat(numbers(second)).containsExactlyElementsOf(range(21, 40));
        assertThat(numbers(read(guest.get(list + "?page=2")))).as("a page by number is the same page")
                .containsExactlyElementsOf(numbers(second));

        JsonNode last = read(guest.get(list + "?after=" + second.path("next").asString()));
        assertThat(numbers(last)).containsExactlyElementsOf(range(41, 45));
        assertThat(last.path("hasMore").asBoolean()).isFalse();
        assertThat(last.path("next").isNull()).isTrue();

        assertThat(numbers(read(guest.get(list + "?order=desc&after=26")))).as("newest first: those before chapter 26")
                .containsExactlyElementsOf(range(6, 25).reversed());
        assertThat(numbers(read(guest.get(list + "?from=10&to=30&after=25")))).as("a volume's bounds hold")
                .containsExactly(26, 27, 28, 29, 30);

        // A chapter taken away leaves a gap the key steps over, and «глав» follows.
        chapters.trashChapter(editionId, 22, adminId);
        assertThat(numbers(read(guest.get(list + "?after=20")))).containsExactlyElementsOf(
                IntStream.rangeClosed(21, 41).filter(n -> n != 22).boxed().toList());
        assertThat(read(guest.get("/api/novels/" + slug)).path("edition").path("chapterCount").asInt()).isEqualTo(44);
        chapters.restoreChapter(editionId, 22);
        assertThat(numbers(read(guest.get(list + "?after=20")))).containsExactlyElementsOf(range(21, 40));
        assertThat(read(guest.get("/api/novels/" + slug)).path("edition").path("chapterCount").asInt()).isEqualTo(45);
    }

    @Test
    void theCatalogGoesByKeyThroughTiesAndNulls() {
        List<Long> editions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // The same title for all: «за назвою» ties too.
            editions.add(editionOf(importNovel("Ключ " + word, 2)));
        }
        OffsetDateTime at = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 123_456_000, ZoneOffset.UTC);
        db.update(EDITION).set(EDITION.LAST_PUBLISHED_AT, at).set(EDITION.CREATED_AT, at).where(EDITION.ID.in(editions)).execute();
        db.update(EDITION).setNull(EDITION.LAST_PUBLISHED_AT).where(EDITION.ID.eq(editions.get(3))).execute();
        for (long tied : List.of(editions.get(1), editions.get(4))) {
            db.insertInto(EDITION_STAT).set(EDITION_STAT.EDITION_ID, tied).set(EDITION_STAT.SHELVED, 3)
                    .onConflict(EDITION_STAT.EDITION_ID).doUpdate().set(EDITION_STAT.SHELVED, 3).execute();
        }
        Views.CatalogFilter filter = Views.CatalogFilter.words(word);

        for (String sort : List.of("popular", "updated", "new", "title", "rating", "relevance")) {
            List<Long> all = queries.search(filter, sort, false, null, 1, 100).items().stream().map(Views.Card::editionId).toList();
            assertThat(all).as(sort).containsExactlyInAnyOrderElementsOf(editions);
            List<Long> walked = new ArrayList<>();
            String after = null;
            int pages = 0;
            while (true) {
                Views.Found<Views.Card> page = queries.search(filter, sort, false, null, 1, after, 2);
                assertThat(page.total() == null).as("the count comes with the first page only").isEqualTo(after != null);
                page.items().forEach(card -> walked.add(card.editionId()));
                pages++;
                if (!page.hasMore()) {
                    assertThat(page.next()).isNull();
                    break;
                }
                after = page.next();
            }
            assertThat(walked).as(sort + ": every novel once, in the order of one long page").containsExactlyElementsOf(all);
            assertThat(pages).isEqualTo(3);
        }

        Browser guest = new Browser(port);
        JsonNode first = read(guest.get("/api/catalog?q=" + word + "&sort=updated"));
        assertThat(first.path("total").asInt()).isEqualTo(5);
        assertThat(first.path("hasMore").asBoolean()).isFalse();
        assertThat(guest.get("/api/catalog?q=" + word + "&after=zzz").status()).as("a cursor of no order").isEqualTo(400);
    }

    @Test
    void commentsGoByKeyNewestFirst() {
        String slug = importNovel("Розмови " + word, 1);
        long editionId = editionOf(slug);
        for (int i = 1; i <= 25; i++) {
            db.insertInto(COMMENT).set(COMMENT.EDITION_ID, editionId).set(COMMENT.AUTHOR_ID, adminId)
                    .set(COMMENT.BODY, "Коментар " + i).set(COMMENT.SCORE, i % 3).execute();
        }
        Browser guest = new Browser(port);
        for (String sort : List.of("new", "top")) {
            String path = "/api/editions/" + editionId + "/comments?sort=" + sort;
            JsonNode first = read(guest.get(path));
            assertThat(first.path("total").asInt()).isEqualTo(25);
            assertThat(first.path("items")).hasSize(20);
            JsonNode second = read(guest.get(path + "&after=" + first.path("next").asString()));
            assertThat(second.path("items")).hasSize(5);
            assertThat(second.path("total").isNull()).isTrue();
            assertThat(second.path("hasMore").asBoolean()).isFalse();
            List<Long> ids = new ArrayList<>();
            first.path("items").forEach(item -> ids.add(item.path("id").asLong()));
            second.path("items").forEach(item -> ids.add(item.path("id").asLong()));
            assertThat(ids).as(sort).doesNotHaveDuplicates().hasSize(25);
        }
    }

    @Test
    void readChaptersAreCountedAheadAndStayRight() {
        String slug = importNovel("Прочитане " + word, 5);
        long editionId = editionOf(slug);
        Person reader = Accounts.signedIn(port, mailbox);
        long readerId = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(reader.nick())).fetchSingle(ACCOUNT.ID);
        String reads = "/api/reads/" + editionId;

        assertThat(reader.browser().put(reads, json("from", 1, "to", 3, "read", true)).status()).isEqualTo(204);
        assertThat(readCount(readerId, editionId)).isEqualTo(3);
        assertThat(reader.browser().put(reads, json("from", 2, "to", 4, "read", true)).status()).as("one already read").isEqualTo(204);
        assertThat(readCount(readerId, editionId)).isEqualTo(4);
        reader.browser().put("/api/progress/" + editionId, json("chapterNumber", 5, "position", 0.95));
        assertThat(readCount(readerId, editionId)).as("read to the end").isEqualTo(5);
        assertThat(cardOf(reader.browser()).path("read").asInt()).as("the card tells it").isEqualTo(5);

        reader.browser().put(reads, json("from", 2, "to", 3, "read", false));
        assertThat(readCount(readerId, editionId)).isEqualTo(3);
        assertThat(reader.browser().delete("/api/progress/" + editionId).status()).isEqualTo(204);
        assertThat(readCount(readerId, editionId)).isZero();
        assertThat(cardOf(reader.browser()).path("read").asInt()).isZero();
    }

    @Test
    void popularityIsCountedAheadAndRecounted() {
        String slug = importNovel("Популярне " + word, 2);
        long editionId = editionOf(slug);
        Person reader = Accounts.signedIn(port, mailbox);
        long readerId = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(reader.nick())).fetchSingle(ACCOUNT.ID);

        reader.browser().put("/api/progress/" + editionId, json("chapterNumber", 1, "position", 0.5));
        assertThat(popularity(editionId)).as("a reader of this month, and «Читаю»").isEqualTo(2 + 1);
        reader.browser().put("/api/progress/" + editionId, json("chapterNumber", 1, "position", 0.6));
        assertThat(popularity(editionId)).as("the same reader once").isEqualTo(3);
        reader.browser().put("/api/library/" + editionId, json("list", "dropped"));
        assertThat(popularity(editionId)).as("dropped is not on the shelf").isEqualTo(2);
        reader.browser().put("/api/library/" + editionId, json("list", "planned"));
        assertThat(popularity(editionId)).isEqualTo(3);

        stats.recount();
        assertThat(popularity(editionId)).as("the recount agrees").isEqualTo(3);
        db.update(READING_PROGRESS).set(READING_PROGRESS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC).minusDays(40))
                .where(READING_PROGRESS.ACCOUNT_ID.eq(readerId), READING_PROGRESS.EDITION_ID.eq(editionId)).execute();
        stats.recount();
        assertThat(popularity(editionId)).as("a month away, the reader drops out").isEqualTo(1);
        reader.browser().put("/api/progress/" + editionId, json("chapterNumber", 1, "position", 0.7));
        assertThat(popularity(editionId)).as("and counts again on coming back").isEqualTo(3);

        reader.browser().put("/api/library/" + editionId, json("list", (Object) null));
        assertThat(popularity(editionId)).isEqualTo(2);
    }

    @Test
    void newWordsAreCountedAhead() {
        String slug = importNovel("Слова " + word, 1);
        long novelId = db.select(EDITION.NOVEL_ID).from(EDITION).where(EDITION.ID.eq(editionOf(slug))).fetchSingle(EDITION.NOVEL_ID);
        List<Long> ids = new ArrayList<>();
        for (String name : List.of("Рьо", "Абель", "Ліса")) {
            ids.add(db.insertInto(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.NOVEL_ID, novelId).set(GLOSSARY_ENTRY.UKRAINIAN, name)
                    .returning(GLOSSARY_ENTRY.ID).fetchSingle(GLOSSARY_ENTRY.ID));
        }
        assertThat(newWords(slug)).isEqualTo(3);
        db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.STATUS, "approved").where(GLOSSARY_ENTRY.ID.eq(ids.get(0))).execute();
        assertThat(newWords(slug)).isEqualTo(2);
        db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.STATUS, "new").where(GLOSSARY_ENTRY.ID.eq(ids.get(1))).execute();
        assertThat(newWords(slug)).as("still new: no change").isEqualTo(2);
        db.deleteFrom(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.ID.eq(ids.get(2))).execute();
        assertThat(newWords(slug)).isEqualTo(1);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private String importNovel(String title, int chapterCount) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= chapterCount; i++) {
            text.append("# Глава ").append(i).append("\nТекст глави ").append(i).append(".\n\n");
        }
        Response response = admin.browser().upload("/api/admin/novels",
                Map.of("title", title, "author", "Автор", "description", "Опис.", "tags", "Фентезі", "kind", "machine", "adult", "false"),
                "file", "novel.md", "text/markdown", text.toString().getBytes(StandardCharsets.UTF_8));
        return read(response).path("slug").asString();
    }

    private long editionOf(String slug) {
        return read(new Browser(port).get("/api/novels/" + slug)).path("edition").path("editionId").asLong();
    }

    private JsonNode cardOf(Browser browser) {
        return read(browser.get("/api/catalog?q=" + word)).path("items").path(0);
    }

    private int readCount(long accountId, long editionId) {
        return db.select(READING_STAT.CHAPTERS_READ).from(READING_STAT)
                .where(READING_STAT.ACCOUNT_ID.eq(accountId), READING_STAT.EDITION_ID.eq(editionId))
                .fetchOptional(READING_STAT.CHAPTERS_READ).orElse(0);
    }

    private int popularity(long editionId) {
        return db.select(EDITION_STAT.POPULARITY).from(EDITION_STAT).where(EDITION_STAT.EDITION_ID.eq(editionId))
                .fetchOptional(EDITION_STAT.POPULARITY).orElse(0);
    }

    private int newWords(String slug) {
        JsonNode mine = read(admin.browser().get("/api/studio"));
        for (JsonNode item : mine) {
            if (item.path("novelSlug").asString().equals(slug)) {
                return item.path("newWords").asInt();
            }
        }
        throw new AssertionError("Not in the Studio: " + slug);
    }

    private static List<Integer> numbers(JsonNode page) {
        List<Integer> out = new ArrayList<>();
        page.path("items").forEach(row -> out.add(row.path("number").asInt()));
        return out;
    }

    private static List<Integer> range(int from, int to) {
        return IntStream.rangeClosed(from, to).boxed().toList();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
