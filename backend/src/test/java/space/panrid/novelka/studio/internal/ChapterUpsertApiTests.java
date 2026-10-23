package space.panrid.novelka.studio.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITOR_DRAFT;
import static space.panrid.novelka.jooq.Tables.IDEMPOTENCY_KEY;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;
import static space.panrid.novelka.jooq.Tables.VOLUME;
import static space.panrid.novelka.support.Browser.json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.platform.web.Idempotency;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import space.panrid.novelka.text.Chapters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 22.10: a chapter by its number in one request — created or its draft replaced, never twice
 * with the same {@code Idempotency-Key}, published only by a request of its own.
 */
@IntegrationTest
class ChapterUpsertApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    Chapters chapters;

    @Autowired
    Idempotency idempotency;

    Person owner;
    String studio;
    String slug;
    String team;
    String base;
    long edition;

    @BeforeEach
    void aTranslationOfItsOwn() {
        owner = Accounts.signedIn(port, mailbox);
        studio = token(owner, List.of("studio"), null);
        slug = "glava-za-nomerom-" + suffix();
        JsonNode novel = read(call("POST", "/api/v1/studio/novels", studio, null,
                JSON.writeValueAsString(Map.of("kind", "human", "title", "Глава за номером", "slug", slug))));
        team = novel.path("team").asString();
        base = "/api/v1/studio/novels/" + slug + "/translations/" + team;
        edition = db.select(EDITION.ID).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).where(NOVEL.SLUG.eq(slug))
                .fetchSingle().value1();
    }

    @Test
    void aMissingChapterIsCreatedAndAnExistingOneGetsANewDraft() {
        HttpResponse<String> created = put(12, studio, null, text("Глава 12", "Перший варіант."));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode saved = JSON.readTree(created.body());
        assertThat(saved.path("number").asInt()).isEqualTo(12);
        assertThat(saved.path("created").asBoolean()).isTrue();
        assertThat(saved.path("published").asBoolean()).isFalse();
        assertThat(saved.path("revision").isNull()).isTrue();
        assertThat(saved.path("draft").path("title").asString()).isEqualTo("Глава 12");
        assertThat(saved.has("blocks")).as("the text is not sent back").isFalse();
        assertThat(created.headers().firstValue(Idempotency.REPLAYED)).as("no key, nothing to replay").isEmpty();
        assertThat(publicChapter(12).statusCode()).as("readers do not see a draft").isEqualTo(404);

        HttpResponse<String> updated = put(12, studio, null, text("Глава 12", "Другий варіант."));
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(JSON.readTree(updated.body()).path("created").asBoolean()).isFalse();
        JsonNode chapter = read(call("GET", base + "/chapters/12", studio, null, null));
        assertThat(firstParagraph(chapter.path("draft"))).isEqualTo("Другий варіант.");
        assertThat(db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(edition))).isEqualTo(1);
        assertThat(db.fetchCount(EDITOR_DRAFT, EDITOR_DRAFT.CHAPTER_ID.in(
                db.select(CHAPTER.ID).from(CHAPTER).where(CHAPTER.EDITION_ID.eq(edition))))).as("one draft, replaced").isEqualTo(1);

        HttpResponse<String> empty = put(12, studio, null, JSON.writeValueAsString(Map.of("title", "Без тексту")));
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(empty.body()).contains("Надішліть текст глави: блоками");
    }

    @Test
    void aPublishedChapterChangesForReadersOnlyWhenPublishedAgain() {
        read(put(1, studio, null, text("Початок", "Що бачать читачі.")));
        read(call("POST", base + "/chapters/1/publish", studio, null, null));
        assertThat(firstParagraph(JSON.readTree(publicChapter(1).body()))).isEqualTo("Що бачать читачі.");

        JsonNode saved = JSON.readTree(put(1, studio, null, text("Початок", "Нова редакція.")).body());
        assertThat(saved.path("created").asBoolean()).isFalse();
        assertThat(saved.path("published").asBoolean()).isTrue();
        assertThat(saved.path("draft").path("baseRevision").asLong()).as("based on what readers see")
                .isEqualTo(saved.path("revision").asLong());
        assertThat(firstParagraph(JSON.readTree(publicChapter(1).body()))).as("not quietly changed").isEqualTo("Що бачать читачі.");

        read(call("POST", base + "/chapters/1/publish", studio, null, null));
        assertThat(firstParagraph(JSON.readTree(publicChapter(1).body()))).isEqualTo("Нова редакція.");
    }

    @Test
    void theSameRequestWithTheSameKeyGetsTheSameAnswerAndChangesNothingTwice() {
        String key = UUID.randomUUID().toString();
        HttpResponse<String> first = put(3, studio, key, text("Третя", "Текст."));
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(first.headers().firstValue(Idempotency.REPLAYED)).hasValue("false");
        OffsetDateTime saved = draftUpdatedAt(3);

        // The same body with the fields in another order and other spacing is the same request.
        String reordered = "{ \"blocks\": " + JSON.writeValueAsString(blocks("Текст.")) + ",\n  \"title\": \"Третя\" }";
        HttpResponse<String> again = put(3, studio, key, reordered);
        assertThat(again.statusCode()).isEqualTo(201);
        assertThat(again.body()).isEqualTo(first.body());
        assertThat(again.headers().firstValue(Idempotency.REPLAYED)).hasValue("true");
        assertThat(draftUpdatedAt(3)).as("the draft is not saved again").isEqualTo(saved);
        assertThat(db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(edition))).isEqualTo(1);

        HttpResponse<String> otherBody = put(3, studio, key, text("Третя", "Інший текст."));
        assertThat(otherBody.statusCode()).isEqualTo(409);
        assertThat(otherBody.body()).contains("idempotency-key-reused").contains("уже використано для іншого запиту");
        HttpResponse<String> otherChapter = put(4, studio, key, text("Третя", "Текст."));
        assertThat(otherChapter.statusCode()).isEqualTo(409);
        assertThat(db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(edition))).as("nothing made by a reused key").isEqualTo(1);
        assertThat(firstParagraph(read(call("GET", base + "/chapters/3", studio, null, null)).path("draft"))).isEqualTo("Текст.");

        assertThat(put(5, studio, "with space", text("П'ята", "Текст.")).statusCode()).isEqualTo(400);
        assertThat(put(5, studio, "x".repeat(256), text("П'ята", "Текст.")).statusCode()).isEqualTo(400);
    }

    @Test
    void aScriptRunTwiceWithTheSameKeysGivesTheSameChaptersAndNoDuplicates() {
        List<String> keys = List.of("run-1-ch-1", "run-1-ch-2", "run-1-ch-3");
        List<String> firstRun = script(keys);
        List<String> secondRun = script(keys);

        assertThat(secondRun).as("the same answers, byte for byte").isEqualTo(firstRun);
        assertThat(firstRun).allSatisfy(answer -> assertThat(answer).startsWith("201 "));
        List<Integer> numbers = db.select(CHAPTER.NUMBER).from(CHAPTER).where(CHAPTER.EDITION_ID.eq(edition)).orderBy(CHAPTER.NUMBER)
                .fetch(CHAPTER.NUMBER);
        assertThat(numbers).containsExactly(1, 2, 3);
        assertThat(read(call("GET", base + "/chapters", studio, null, null)).path("items").size()).isEqualTo(3);

        // Without keys a second run is safe too — the chapters are found by number — only the answers say «updated».
        List<String> unkeyed = script(java.util.Arrays.asList(null, null, null));
        assertThat(unkeyed).allSatisfy(answer -> assertThat(answer).startsWith("200 "));
        assertThat(db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(edition))).isEqualTo(3);
    }

    @Test
    void twoRequestsWithOneKeyAtOnceDoTheWorkOnce() {
        String key = "at-once-" + suffix();
        String body = text("Одночасно", "Текст.");
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sent.add(HTTP.sendAsync(request("PUT", base + "/chapters/7", studio, key, body).build(), HttpResponse.BodyHandlers.ofString()));
        }
        List<HttpResponse<String>> answers = sent.stream().map(CompletableFuture::join).toList();
        assertThat(answers).extracting(HttpResponse::statusCode).containsOnly(201);
        assertThat(answers).extracting(HttpResponse::body).containsOnly(answers.getFirst().body());
        assertThat(db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(edition))).isEqualTo(1);
    }

    @Test
    void aRefusedRequestDoesNotTakeItsKeyAndOldKeysAreForgotten() {
        long trashed = chapterAt(2);
        chapters.trashChapter(edition, 2, accountId(owner));
        String key = "after-refusal-" + suffix();
        HttpResponse<String> inTrash = put(2, studio, key, text("Друга", "Текст."));
        assertThat(inTrash.statusCode()).isEqualTo(409);
        assertThat(inTrash.body()).contains("chapter-in-trash").contains("у кошику");
        assertThat(db.fetchExists(IDEMPOTENCY_KEY, IDEMPOTENCY_KEY.KEY.eq(key))).as("a refusal is not kept").isFalse();
        chapters.restoreChapter(edition, 2);
        HttpResponse<String> restored = put(2, studio, key, text("Друга", "Текст."));
        assertThat(restored.statusCode()).as("the key works once the chapter is back").isEqualTo(200);
        assertThat(chapterAt(2)).as("the same chapter, not a new one").isEqualTo(trashed);

        String old = "old-" + suffix();
        read(put(8, studio, old, text("Восьма", "Текст.")));
        db.update(IDEMPOTENCY_KEY).set(IDEMPOTENCY_KEY.CREATED_AT, OffsetDateTime.now().minus(Idempotency.KEPT).minusHours(1))
                .where(IDEMPOTENCY_KEY.KEY.eq(old)).execute();
        HttpResponse<String> later = put(8, studio, old, text("Восьма", "Новий текст через тиждень."));
        assertThat(later.statusCode()).as("a key older than a week is free again").isEqualTo(200);

        String swept = "swept-" + suffix();
        read(put(9, studio, swept, text("Дев'ята", "Текст.")));
        db.update(IDEMPOTENCY_KEY).set(IDEMPOTENCY_KEY.CREATED_AT, OffsetDateTime.now().minus(Idempotency.KEPT).minusHours(1))
                .where(IDEMPOTENCY_KEY.KEY.eq(swept)).execute();
        idempotency.forgetOld();
        assertThat(db.fetchExists(IDEMPOTENCY_KEY, IDEMPOTENCY_KEY.KEY.eq(swept))).isFalse();
        assertThat(db.fetchExists(IDEMPOTENCY_KEY, IDEMPOTENCY_KEY.KEY.eq(old))).as("renewed a moment ago").isTrue();
    }

    @Test
    void anyFreePlaceIsTakenWithoutMovingAnotherChapter() {
        // A prologue at 1, the first volume from 2: readers see «Пролог», 1, 2, 3.
        db.insertInto(VOLUME).set(VOLUME.EDITION_ID, edition).set(VOLUME.FIRST_NUMBER, 1).set(VOLUME.KIND, "prologue").execute();
        db.insertInto(VOLUME).set(VOLUME.EDITION_ID, edition).set(VOLUME.FIRST_NUMBER, 2).set(VOLUME.KIND, "volume").execute();
        assertThat(JSON.readTree(put(1, studio, null, text("Пролог", "Текст.")).body()).path("label").asString()).isEmpty();
        read(put(2, studio, null, text("Перша", "Текст.")));
        JsonNode gap = JSON.readTree(put(10, studio, null, text("Після пропуску", "Текст.")).body());
        assertThat(gap.path("number").asInt()).isEqualTo(10);
        assertThat(gap.path("label").asString()).isEqualTo("9");

        JsonNode middle = JSON.readTree(put(5, studio, null, text("Посередині", "Текст.")).body());
        assertThat(middle.path("created").asBoolean()).isTrue();
        assertThat(middle.path("label").asString()).isEqualTo("4");
        assertThat(db.select(CHAPTER.NUMBER, CHAPTER.LABEL).from(CHAPTER).where(CHAPTER.EDITION_ID.eq(edition)).orderBy(CHAPTER.NUMBER)
                .fetch(r -> r.value1() + ":" + r.value2())).as("addresses stay").containsExactly("1:", "2:1", "5:4", "10:9");

        HttpResponse<String> zero = put(0, studio, null, text("Нуль", "Текст."));
        assertThat(zero.statusCode()).isEqualTo(400);
        assertThat(put(100_000, studio, null, text("Задалеко", "Текст.")).body()).contains("від 1 до 99 999");

        db.update(EDITION).set(EDITION.FIRST_NUMBER, 50).where(EDITION.ID.eq(edition)).execute();
        HttpResponse<String> beforeRelay = put(20, studio, null, text("До естафети", "Текст."));
        assertThat(beforeRelay.statusCode()).isEqualTo(400);
        assertThat(beforeRelay.body()).contains("продовжує попередній із глави 50");
        assertThat(put(5, studio, null, text("Посередині", "Оновлено.")).statusCode()).as("an existing one is still updated").isEqualTo(200);
    }

    @Test
    void theTeamsRulesAndTheTokensLimitsHold() {
        read(put(1, studio, null, text("Перша", "Текст.")));
        Person editor = Accounts.signedIn(port, mailbox);
        long teamId = db.select(EDITION.TEAM_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1();
        db.insertInto(TEAM_MEMBER).set(TEAM_MEMBER.TEAM_ID, teamId).set(TEAM_MEMBER.ACCOUNT_ID, accountId(editor))
                .set(TEAM_MEMBER.ROLE, "editor").execute();
        String editors = token(editor, List.of("studio"), null);
        assertThat(put(1, editors, null, text("Перша", "Правка редактора.")).statusCode()).as("an editor edits").isEqualTo(200);
        HttpResponse<String> newOne = put(2, editors, null, text("Друга", "Текст."));
        assertThat(newOne.statusCode()).as("but does not add chapters").isEqualTo(403);
        assertThat(newOne.body()).contains("додають лише власник і перекладачі");

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(put(1, token(stranger, List.of("studio")), null, text("Чужа", "Текст.")).statusCode()).isEqualTo(403);
        HttpResponse<String> reading = put(1, token(owner, List.of("read"), null), null, text("Перша", "Текст."));
        assertThat(reading.statusCode()).isEqualTo(403);
        assertThat(reading.body()).contains("«studio»");

        String otherSlug = "insha-" + suffix();
        JsonNode other = read(call("POST", "/api/v1/studio/novels", studio, null,
                JSON.writeValueAsString(Map.of("kind", "human", "title", "Інша", "slug", otherSlug))));
        long otherEdition = db.select(EDITION.ID).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(NOVEL.SLUG.eq(otherSlug)).fetchSingle().value1();
        String bound = token(owner, List.of("studio"), otherEdition);
        HttpResponse<String> elsewhere = put(1, bound, null, text("Перша", "Текст."));
        assertThat(elsewhere.statusCode()).as("a token limited to another translation").isEqualTo(403);
        assertThat(elsewhere.body()).contains("Токен обмежено перекладом");
        assertThat(call("PUT", "/api/v1/studio/novels/" + otherSlug + "/translations/" + other.path("team").asString() + "/chapters/1",
                bound, null, text("Своя", "Текст.")).statusCode()).as("its own one").isEqualTo(201);
    }

    // ---- helpers -----------------------------------------------------------------------------------

    /** A script sending three chapters: the status and body of each answer. */
    private List<String> script(List<String> keys) {
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            HttpResponse<String> answer = put(i + 1, studio, keys.get(i), text("Глава " + (i + 1), "Текст глави " + (i + 1) + "."));
            answers.add(answer.statusCode() + " " + answer.body());
        }
        return answers;
    }

    private HttpResponse<String> put(int number, String token, String key, String body) {
        return call("PUT", base + "/chapters/" + number, token, key, body);
    }

    private HttpResponse<String> publicChapter(int number) {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/novels/" + slug + "/translations/" + team
                + "/chapters/" + number)).header("Accept", "application/json"));
    }

    private static String text(String title, String paragraph) {
        return JSON.writeValueAsString(Map.of("title", title, "blocks", blocks(paragraph)));
    }

    private static List<Map<String, Object>> blocks(String paragraph) {
        return List.of(Map.of("type", "paragraph", "content", List.of(Map.of("text", paragraph))));
    }

    private static String firstParagraph(JsonNode text) {
        return text.path("blocks").path(0).path("content").path(0).path("text").asString();
    }

    private OffsetDateTime draftUpdatedAt(int number) {
        return db.select(EDITOR_DRAFT.UPDATED_AT).from(EDITOR_DRAFT).join(CHAPTER).on(CHAPTER.ID.eq(EDITOR_DRAFT.CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(edition), CHAPTER.NUMBER.eq(number)).fetchSingle().value1();
    }

    private long chapterAt(int number) {
        Long id = db.select(CHAPTER.ID).from(CHAPTER).where(CHAPTER.EDITION_ID.eq(edition), CHAPTER.NUMBER.eq(number)).fetchOne(CHAPTER.ID);
        if (id != null) {
            return id;
        }
        read(put(number, studio, null, text("Глава " + number, "Текст.")));
        return chapterAt(number);
    }

    private long accountId(Person person) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(person.nick())).fetchSingle().value1();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String token(Person person, List<String> scopes) {
        return token(person, scopes, null);
    }

    private static String token(Person person, List<String> scopes, Long edition) {
        String body = edition == null ? json("name", "Скрипт", "scopes", scopes, "days", 7)
                : json("name", "Скрипт", "scopes", scopes, "days", 7, "edition", edition);
        var response = person.browser().post("/api/me/api-tokens", body);
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body()).path("secret").asString();
    }

    private HttpRequest.Builder request(String method, String path, String token, String key, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (key != null) {
            request.header(Idempotency.HEADER, key);
        }
        return request;
    }

    private HttpResponse<String> call(String method, String path, String token, String key, String body) {
        return send(request(method, path, token, key, body));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode read(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.nullNode() : JSON.readTree(response.body());
    }
}
