package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_LINK;
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
 * Етап 24.4 (issue #332): перенесення авторських матеріалів (пробний імпорт, ідемпотентний повтор,
 * зіставлення посилань між документами та главами, збереження поточної публічності й рукопису).
 */
@IntegrationTest
class WorkspaceImportTests {

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
    String studioToken;

    record Work(String novel, String team, long editionId) {
        String base() {
            return "/api/v1/studio/novels/" + novel + "/translations/" + team + "/workspace";
        }

        String chaptersBase() {
            return "/api/v1/studio/novels/" + novel + "/translations/" + team + "/chapters";
        }
    }

    @BeforeEach
    void setUp() {
        author = Accounts.signedIn(port, mailbox);
        studioToken = token(author, List.of("studio"));
    }

    @Test
    void dryRunPreviewDoesNotPersistAndResolvesLinksAcrossBatchAndChapters() {
        Work work = createWork(author, "Хроніки Зоряної Вежі " + suffix());

        // Create and publish chapter 1, and keep chapter 2 as an unpublished draft
        put(work.chaptersBase() + "/1", studioToken, json(
                "title", "Перший промінь",
                "text", "Зоряна вежа світилася в тумані.",
                "format", "markdown"
        ));
        post(work.chaptersBase() + "/1/publish", studioToken, null);

        put(work.chaptersBase() + "/2", studioToken, json(
                "title", "Таємна зала",
                "text", "Усередині вежі спали стародавні механізми.",
                "format", "markdown"
        ));

        byte[] imgBytes = Pictures.png(320, 320, Color.BLUE);
        long imageId = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"),
                "file", "tower.png", "image/png", imgBytes)).path("id").asLong();

        String batchBody = JSON.writeValueAsString(Map.of(
                "dryRun", true,
                "documents", List.of(
                        Map.of(
                                "slug", "karta-znan",
                                "title", "00_Карта Знань",
                                "kind", "meta",
                                "content", "Огляд світу: [[01_Канон/Закони Світла]], [[02_Плани/План Вежі#Частина 1]] та [[04_Рукопис/Розділ 1 - Перший промінь]]."
                        ),
                        Map.of(
                                "slug", "zakony-svitla",
                                "title", "Закони Світла",
                                "kind", "canon",
                                "content", "Світло резонує з кристалами. Див. [[#Резонанс]] та [[tower_concept.png]]."
                        ),
                        Map.of(
                                "slug", "plan-vezhi",
                                "title", "План Вежі",
                                "kind", "plan",
                                "content", "Події ведуть до [[Розділ 2]]. Посилання на [[Неіснуючий документ]]."
                        )
                ),
                "attachments", List.of(
                        Map.of(
                                "imageId", imageId,
                                "name", "tower_concept.png",
                                "description", "Концепт Зоряної Вежі",
                                "documentSlug", "zakony-svitla"
                        )
                )
        ));

        JsonNode preview = read(post(work.base() + "/import", studioToken, batchBody));
        assertThat(preview.path("dryRun").asBoolean()).isTrue();
        assertThat(preview.path("created").asInt()).isEqualTo(3);
        assertThat(preview.path("updated").asInt()).isEqualTo(0);
        assertThat(preview.path("unchanged").asInt()).isEqualTo(0);
        assertThat(preview.path("attachmentsCreated").asInt()).isEqualTo(1);

        // Verify link resolution in dryRun preview
        JsonNode doc0Links = preview.path("documents").get(0).path("links");
        assertThat(doc0Links).hasSize(3);
        assertThat(doc0Links.get(0).path("resolvedDocumentSlug").asString()).isEqualTo("zakony-svitla");
        assertThat(doc0Links.get(0).path("resolved").asBoolean()).isTrue();
        assertThat(doc0Links.get(1).path("resolvedDocumentSlug").asString()).isEqualTo("plan-vezhi");
        assertThat(doc0Links.get(1).path("resolved").asBoolean()).isTrue();
        assertThat(doc0Links.get(2).path("resolvedChapterNumber").asInt()).isEqualTo(1);
        assertThat(doc0Links.get(2).path("resolved").asBoolean()).isTrue();

        // Anchor and attachment link in doc1
        JsonNode doc1Links = preview.path("documents").get(1).path("links");
        assertThat(doc1Links).hasSize(2);
        assertThat(doc1Links.get(0).path("resolvedDocumentSlug").asString()).isEqualTo("zakony-svitla");
        assertThat(doc1Links.get(0).path("resolved").asBoolean()).isTrue();
        assertThat(doc1Links.get(1).path("resolved").asBoolean()).isTrue();

        // Unresolved link in doc2 is flagged as resolved=false
        JsonNode doc2Links = preview.path("documents").get(2).path("links");
        assertThat(doc2Links).hasSize(2);
        assertThat(doc2Links.get(0).path("resolvedChapterNumber").asInt()).isEqualTo(2);
        assertThat(doc2Links.get(0).path("resolved").asBoolean()).isTrue();
        assertThat(doc2Links.get(1).path("rawTarget").asString()).isEqualTo("Неіснуючий документ");
        assertThat(doc2Links.get(1).path("resolved").asBoolean()).isFalse();

        // Ensure nothing was persisted during dryRun
        JsonNode listed = read(get(work.base() + "/documents", studioToken));
        assertThat(listed.path("items")).isEmpty();
        assertThat(read(get(work.base() + "/attachments", studioToken))).isEmpty();
    }

    @Test
    void actualImportIsIdempotentPreservesVisibilityAndChaptersAndExportsCleanly() {
        Work work = createWork(author, "Спадщина Коваля " + suffix());

        // Set visibility to public and publish chapter 1, keep chapter 2 as draft
        author.browser().put("/api/studio/editions/" + work.editionId() + "/visibility", json("visibility", "public"));
        put(work.chaptersBase() + "/1", studioToken, json(
                "title", "Стара кузня",
                "text", "Вогонь у горні горів рівно.",
                "format", "markdown"
        ));
        post(work.chaptersBase() + "/1/publish", studioToken, null);
        put(work.chaptersBase() + "/2", studioToken, json(
                "title", "Холодна криця",
                "text", "Чернетка другої глави.",
                "format", "markdown"
        ));

        byte[] imgBytes = Pictures.png(300, 300, Color.ORANGE);
        long imageId = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"),
                "file", "anvil.png", "image/png", imgBytes)).path("id").asLong();

        List<Map<String, Object>> docs = List.of(
                Map.of(
                        "slug", "stan-roboty",
                        "title", "00_Стан роботи",
                        "kind", "status",
                        "content", "Опубліковано [[Розділ 1]]. Готується [[01_Канон/Таємниці Криці]]."
                ),
                Map.of(
                        "slug", "taiemnytsi-krytsi",
                        "title", "Таємниці Криці",
                        "kind", "canon",
                        "content", "Криця кується лише при повному місяці. Див. [[00_Стан роботи]]."
                )
        );
        List<Map<String, Object>> atts = List.of(
                Map.of(
                        "imageId", imageId,
                        "name", "01_Ковадло.png",
                        "description", "Ескіз ковадла",
                        "documentSlug", "taiemnytsi-krytsi"
                )
        );

        // 1. First run (dryRun = false)
        JsonNode run1 = read(post(work.base() + "/import", studioToken, JSON.writeValueAsString(Map.of(
                "dryRun", false,
                "documents", docs,
                "attachments", atts
        ))));
        assertThat(run1.path("dryRun").asBoolean()).isFalse();
        assertThat(run1.path("created").asInt()).isEqualTo(2);
        assertThat(run1.path("updated").asInt()).isEqualTo(0);
        assertThat(run1.path("unchanged").asInt()).isEqualTo(0);
        assertThat(run1.path("attachmentsCreated").asInt()).isEqualTo(1);
        assertThat(run1.path("attachmentsUnchanged").asInt()).isEqualTo(0);

        // 2. Second run with exact same payload -> 0 created, 0 updated, 2 unchanged, 1 attachment unchanged
        JsonNode run2 = read(post(work.base() + "/import", studioToken, JSON.writeValueAsString(Map.of(
                "dryRun", false,
                "documents", docs,
                "attachments", atts
        ))));
        assertThat(run2.path("created").asInt()).isEqualTo(0);
        assertThat(run2.path("updated").asInt()).isEqualTo(0);
        assertThat(run2.path("unchanged").asInt()).isEqualTo(2);
        assertThat(run2.path("attachmentsCreated").asInt()).isEqualTo(0);
        assertThat(run2.path("attachmentsUnchanged").asInt()).isEqualTo(1);

        // Verify revisions were not duplicated (still version 1, revisionsCount 1)
        JsonNode docCheck = read(get(work.base() + "/documents/taiemnytsi-krytsi", studioToken));
        assertThat(docCheck.path("version").asLong()).isEqualTo(1);
        assertThat(docCheck.path("revisionsCount").asInt()).isEqualTo(1);

        // Verify cross-document links in database are resolved to target document IDs
        int resolvedDocLinks = db.fetchCount(WORKSPACE_LINK,
                WORKSPACE_LINK.TARGET_DOCUMENT_ID.isNotNull().and(WORKSPACE_LINK.RAW_TARGET.in(
                        "01_Канон/Таємниці Криці", "00_Стан роботи")));
        assertThat(resolvedDocLinks).isEqualTo(2);

        // Verify visibility and chapter publication states were NOT modified
        String vis = db.select(EDITION.VISIBILITY).from(EDITION).where(EDITION.ID.eq(work.editionId())).fetchSingle(EDITION.VISIBILITY);
        assertThat(vis).isEqualTo("public");
        JsonNode chaptersList = read(get(work.chaptersBase(), studioToken));
        assertThat(chaptersList.path("items")).hasSize(2);
        assertThat(chaptersList.path("items").get(0).path("published").asBoolean()).isTrue();
        assertThat(chaptersList.path("items").get(1).path("published").asBoolean()).isFalse();

        // Verify full export contains all documents and attachments
        JsonNode exported = read(get(work.base() + "/export", studioToken));
        assertThat(exported.path("documents")).hasSize(2);
        assertThat(exported.path("attachments")).hasSize(1);
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

    private Reply post(String path, String token, String body) {
        return send("POST", path, token, null, body);
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
