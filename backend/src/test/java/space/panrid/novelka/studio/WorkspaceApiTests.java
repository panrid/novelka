package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.platform.web.Idempotency;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 24.3 (issue #331): REST API авторського простору (пошук, читання, версії, ревізії, експорт, токени).
 */
@IntegrationTest
class WorkspaceApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    record Reply(int status, String body, String replayed) {
    }

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person author;
    Person stranger;
    Person invited;
    String studioToken;
    String readToken;

    record Work(String novel, String team, long editionId) {
        String base() {
            return "/api/v1/studio/novels/" + novel + "/translations/" + team + "/workspace";
        }
    }

    @BeforeEach
    void setUp() {
        author = Accounts.signedIn(port, mailbox);
        stranger = Accounts.signedIn(port, mailbox);
        invited = Accounts.signedIn(port, mailbox);
        studioToken = token(author, List.of("studio"));
        readToken = token(author, List.of("read"));
    }

    @Test
    void listAndSearchWorkspaceDocumentsPaged() {
        Work work = createWork(author, "Світ Магії " + suffix());

        // 1. Create documents
        put(work.base() + "/documents/stan-roboty", studioToken, json(
                "title", "Стан роботи",
                "kind", "status",
                "content", "Поточна зупинка: розділ 3. Герой Лукас досліджує джерело мани.",
                "version", null
        ));

        put(work.base() + "/documents/zakony-mahii", studioToken, json(
                "title", "Закони Магії",
                "kind", "canon",
                "content", "Мана Лукаса відчувається прохолодною. Посилання на [[stan-roboty|Стан роботи]].",
                "version", null
        ));

        put(work.base() + "/documents/plan-epokhy", studioToken, json(
                "title", "План епохи",
                "kind", "plan",
                "content", "Епоха 1: Пробудження та перші кроки на новому континенті.",
                "version", null
        ));

        // 2. Pagination: page 1 with limit=2
        JsonNode page1 = read(get(work.base() + "/documents?limit=2", studioToken));
        assertThat(page1.path("items")).hasSize(2);
        String next = page1.path("next").asString(null);
        assertThat(next).isNotNull();

        // Page 2 using after=next
        JsonNode page2 = read(get(work.base() + "/documents?limit=2&after=" + next, studioToken));
        assertThat(page2.path("items")).hasSize(1);
        assertThat(page2.path("next").isNull()).isTrue();

        // 3. Search by query 'Лукас' with snippets
        JsonNode search = read(get(work.base() + "/documents?q=Лукас", studioToken));
        assertThat(search.path("items")).hasSize(2);
        assertThat(search.path("items").get(0).path("snippet").asString()).contains("Лукас");

        // 4. Filter by kind
        JsonNode canon = read(get(work.base() + "/documents?kind=canon", studioToken));
        assertThat(canon.path("items")).hasSize(1);
        assertThat(canon.path("items").get(0).path("slug").asString()).isEqualTo("zakony-mahii");
    }

    @Test
    void getSingleDocumentWithLinksAndAttachments() {
        Work work = createWork(author, "Один документ " + suffix());

        put(work.base() + "/documents/pravyla", studioToken, json(
                "title", "Правила світу",
                "kind", "canon",
                "content", "Світ має внутрішні закони. Посилання на [[karta-svitu|Карту Світу]].",
                "version", null
        ));

        JsonNode doc = read(get(work.base() + "/documents/pravyla", studioToken));
        assertThat(doc.path("slug").asString()).isEqualTo("pravyla");
        assertThat(doc.path("title").asString()).isEqualTo("Правила світу");
        assertThat(doc.path("kind").asString()).isEqualTo("canon");
        assertThat(doc.path("version").asLong()).isEqualTo(1);
        assertThat(doc.path("revisionsCount").asInt()).isEqualTo(1);
        assertThat(doc.path("links")).extracting(JsonNode::asString).contains("karta-svitu");
    }

    @Test
    void optimisticLockingAndConcurrentConflictInApi() {
        Work work = createWork(author, "Паралельні правки " + suffix());

        // Create initial document (v1)
        put(work.base() + "/documents/karta-znan", studioToken, json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Версія 1 від автора.",
                "version", null
        ));

        // Concurrent update saves version 2
        put(work.base() + "/documents/karta-znan", studioToken, json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Версія 2 від колеги.",
                "version", 1
        ));

        // Author tries to update using stale version 1 -> 409 Conflict
        Reply conflict = putResponse(work.base() + "/documents/karta-znan", studioToken, null, json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Моя паралельна версія.",
                "version", 1
        ));
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body()).contains("змінено іншим учасником");

        // Resolving conflict with expected version 2
        Reply resolved = putResponse(work.base() + "/documents/karta-znan", studioToken, null, json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Об'єднана версія.",
                "version", 2
        ));
        assertThat(resolved.status()).isEqualTo(200);
        JsonNode current = read(get(work.base() + "/documents/karta-znan", studioToken));
        assertThat(current.path("version").asLong()).isEqualTo(3);
    }

    @Test
    void idempotentRetryWithIdempotencyKey() {
        Work work = createWork(author, "Повтор запиту " + suffix());
        String idempotencyKey = UUID.randomUUID().toString();

        String body = json(
                "title", "Безпечний документ",
                "kind", "status",
                "content", "Текст документа для безпечного повтору.",
                "version", null
        );

        // First attempt with key
        Reply first = putResponse(work.base() + "/documents/bezpeka", studioToken, idempotencyKey, body);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.replayed()).isEqualTo("false");

        // Replayed attempt with same key and same body
        Reply replayed = putResponse(work.base() + "/documents/bezpeka", studioToken, idempotencyKey, body);
        assertThat(replayed.status()).isEqualTo(200);
        assertThat(replayed.replayed()).isEqualTo("true");

        // Document stayed at version 1
        JsonNode doc = read(get(work.base() + "/documents/bezpeka", studioToken));
        assertThat(doc.path("version").asLong()).isEqualTo(1);
        assertThat(doc.path("revisionsCount").asInt()).isEqualTo(1);
    }

    @Test
    void revisionHistoryAndRestoreViaApi() {
        Work work = createWork(author, "Ревізії " + suffix());

        // Rev 1
        put(work.base() + "/documents/hronika", studioToken, json(
                "title", "Хроніка",
                "kind", "timeline",
                "content", "Рік 100: Заснування імперії.",
                "version", null,
                "comment", "Початковий запис"
        ));

        // Rev 2
        put(work.base() + "/documents/hronika", studioToken, json(
                "title", "Хроніка",
                "kind", "timeline",
                "content", "Рік 100: Заснування імперії. Рік 120: Велика війна.",
                "version", 1,
                "comment", "Додано війну"
        ));

        // Rev 3
        put(work.base() + "/documents/hronika", studioToken, json(
                "title", "Хроніка",
                "kind", "timeline",
                "content", "Рік 100: Заснування імперії. Рік 120: Велика війна. Рік 150: Мир.",
                "version", 2,
                "comment", "Додано мир"
        ));

        // Check revisions list
        JsonNode revisions = read(get(work.base() + "/documents/hronika/revisions", studioToken));
        assertThat(revisions.path("items")).hasSize(3);
        long rev1Id = revisions.path("items").get(2).path("id").asLong();

        // Restore to Rev 1
        Reply restored = post(work.base() + "/documents/hronika/revisions/" + rev1Id + "/restore?version=3", studioToken, null);
        assertThat(restored.status()).isEqualTo(200);

        JsonNode current = read(get(work.base() + "/documents/hronika", studioToken));
        assertThat(current.path("version").asLong()).isEqualTo(4);
        assertThat(current.path("content").asString()).isEqualTo("Рік 100: Заснування імперії.");
    }

    @Test
    void attachmentsHandlingAndMediaProtectionViaApi() {
        Work work = createWork(author, "Вкладення " + suffix());

        byte[] imgBytes = Pictures.png(400, 400, Color.CYAN);
        long imageId = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"),
                "file", "art.png", "image/png", imgBytes)).path("id").asLong();

        // Add attachment
        JsonNode attached = read(post(work.base() + "/attachments", studioToken, json(
                "imageId", imageId,
                "name", "Ілюстрація героя",
                "description", "Головний герой Лукас"
        )));
        long attId = attached.path("id").asLong();
        assertThat(attached.path("name").asString()).isEqualTo("Ілюстрація героя");

        // List attachments
        JsonNode list = read(get(work.base() + "/attachments", studioToken));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("id").asLong()).isEqualTo(attId);

        // Delete attachment
        Reply deleted = delete(work.base() + "/attachments/" + attId, studioToken);
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(read(get(work.base() + "/attachments", studioToken))).isEmpty();
    }

    @Test
    void exportWorkspaceMaterialsViaApi() {
        Work work = createWork(author, "Експорт " + suffix());

        put(work.base() + "/documents/kanon", studioToken, json(
                "title", "Канон",
                "kind", "canon",
                "content", "Текст канону для експорту.",
                "version", null
        ));

        byte[] imgBytes = Pictures.png(300, 300, Color.GREEN);
        long imageId = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"),
                "file", "green.png", "image/png", imgBytes)).path("id").asLong();
        post(work.base() + "/attachments", studioToken, json("imageId", imageId, "name", "Емблема"));

        JsonNode export = read(get(work.base() + "/export", studioToken));
        assertThat(export.path("novel").asString()).isEqualTo(work.novel());
        assertThat(export.path("team").asString()).isEqualTo(work.team());
        assertThat(export.path("documents")).hasSize(1);
        assertThat(export.path("documents").get(0).path("slug").asString()).isEqualTo("kanon");
        assertThat(export.path("attachments")).hasSize(1);
        assertThat(export.path("attachments").get(0).path("name").asString()).isEqualTo("Емблема");
    }

    @Test
    void tokenScopeRestrictionsAndLimitedTokenSecurity() {
        Work mine = createWork(author, "Мій твір " + suffix());
        Work other = createWork(stranger, "Чужий твір " + suffix());

        // Make mine private and invite reader
        author.browser().put("/api/studio/editions/" + mine.editionId() + "/visibility", json("visibility", "private"));
        author.browser().post("/api/studio/editions/" + mine.editionId() + "/readers", json("nick", invited.nick()));

        put(mine.base() + "/documents/secret", studioToken, json(
                "title", "Секретний канон",
                "kind", "canon",
                "content", "Тільки для команди.",
                "version", null
        ));

        // 1. Unauthenticated guest -> 401
        assertThat(get(mine.base() + "/documents", null).status()).isEqualTo(401);

        // 2. Read-only token -> 403 Forbidden (requires scope 'studio')
        Reply readRefused = get(mine.base() + "/documents", readToken);
        assertThat(readRefused.status()).isEqualTo(403);
        assertThat(readRefused.body()).contains("«studio»");

        // 3. Stranger's studio token on private work -> 404 Not Found
        String strangerToken = token(stranger, List.of("studio"));
        assertThat(get(mine.base() + "/documents", strangerToken).status()).isEqualTo(404);

        // 4. Invited reader's token on private work -> 404 (workspace materials are team-only!)
        String invitedToken = token(invited, List.of("studio"));
        assertThat(get(mine.base() + "/documents", invitedToken).status()).isEqualTo(404);

        // 5. Token limited to one translation (mine)
        String limitedToken = read(author.browser().post("/api/me/api-tokens", json(
                "name", "Агент твору",
                "scopes", List.of("studio"),
                "days", 7,
                "edition", mine.editionId()
        ))).path("secret").asString();

        // Access to mine works
        assertThat(get(mine.base() + "/documents", limitedToken).status()).isEqualTo(200);

        // Access to other translation is refused with 403
        Reply limitedOther = get(other.base() + "/documents", limitedToken);
        assertThat(limitedOther.status()).isEqualTo(403);
        assertThat(limitedOther.body()).contains("Токен обмежено перекладом");
    }

    private Work createWork(Person person, String title) {
        JsonNode created = read(person.browser().post("/api/studio/editions", """
                {"kind":"original","title":"%s","description":[],"tags":["Фентезі"]}""".formatted(title)));
        long editionId = created.path("editionId").asLong();
        String slug = created.path("novelSlug").asString();
        String team = db.select(TEAM.HANDLE).from(EDITION).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(EDITION.ID.eq(editionId)).fetchSingle(TEAM.HANDLE);
        return new Work(slug, team, editionId);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private static String token(Person person, List<String> scopes) {
        return read(person.browser().post("/api/me/api-tokens", json("name", "Токен", "scopes", scopes, "days", 7)))
                .path("secret").asString();
    }

    private Reply get(String path, String token) {
        return send("GET", path, token, null, null);
    }

    private Reply put(String path, String token, String body) {
        return send("PUT", path, token, null, body);
    }

    private Reply putResponse(String path, String token, String idempotencyKey, String body) {
        return send("PUT", path, token, idempotencyKey, body);
    }

    private Reply post(String path, String token, String body) {
        return send("POST", path, token, null, body);
    }

    private Reply delete(String path, String token) {
        return send("DELETE", path, token, null, null);
    }

    private Reply send(String method, String path, String token, String idempotencyKey, String body) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            req.header(Idempotency.HEADER, idempotencyKey);
        }
        boolean hasBody = body != null && !method.equals("GET") && !method.equals("DELETE");
        req.method(method, hasBody ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        if (hasBody) {
            req.header("Content-Type", "application/json");
        }
        try {
            HttpResponse<String> response = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            String replayed = response.headers().firstValue(Idempotency.REPLAYED).orElse(null);
            return new Reply(response.statusCode(), response.body(), replayed == null ? "" : replayed);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode read(Reply response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.nullNode() : JSON.readTree(response.body());
    }

    private static JsonNode read(space.panrid.novelka.support.Browser.Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.nullNode() : JSON.readTree(response.body());
    }
}
