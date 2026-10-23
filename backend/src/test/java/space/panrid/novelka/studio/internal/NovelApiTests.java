package space.panrid.novelka.studio.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.API_TOKEN;
import static space.panrid.novelka.jooq.Tables.API_TOKEN_WRITE;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.studio.NovelChanged;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.NovelEvents;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 22.9: a work from nothing through the API — created, changed, given a state and a cover with a
 * token alone, under the same rules as in the Studio, and shown there as one made on the site.
 */
@IntegrationTest
class NovelApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    NovelEvents events;

    Person author;
    String studio;

    @BeforeEach
    void anAuthorWithAStudioToken() {
        author = Accounts.signedIn(port, mailbox);
        studio = token(author, List.of("studio"));
    }

    @Test
    void onlyAStudioTokenOfTheTeamsOwnerOrTranslatorCreates() {
        String body = JSON.writeValueAsString(Map.of("kind", "human", "title", "Без права"));
        assertThat(new Browser(port).post("/api/v1/studio/novels", body).status()).as("no token, no session").isEqualTo(401);

        Response reading = bearer("POST", "/api/v1/studio/novels", token(author, List.of("read")), body);
        assertThat(reading.status()).isEqualTo(403);
        assertThat(reading.body()).contains("«studio»");

        String team = read(bearer("POST", "/api/v1/studio/novels", studio, newNovel("human", null))).path("team").asString();
        Person stranger = Accounts.signedIn(port, mailbox);
        Response foreign = bearer("POST", "/api/v1/studio/novels", token(stranger, List.of("studio")),
                JSON.writeValueAsString(Map.of("kind", "human", "title", "У чужій команді", "team", team)));
        assertThat(foreign.status()).as("someone else's team").isEqualTo(403);
        assertThat(foreign.body()).contains("лише власник і перекладачі");

        Response nowhere = bearer("POST", "/api/v1/studio/novels", studio,
                JSON.writeValueAsString(Map.of("kind", "human", "title", "Нікуди", "team", "no-such-team-" + suffix())));
        assertThat(nowhere.status()).isEqualTo(404);
        assertThat(nowhere.body()).contains("Такої команди немає");
    }

    @Test
    void aTranslationMadeByTokenIsInTheStudioAsIfMadeOnTheSite() {
        String slug = "mag-vody-" + suffix();
        Response made = bearer("POST", "/api/v1/studio/novels", studio, JSON.writeValueAsString(Map.of(
                "kind", "human", "title", "Маг води", "slug", slug, "tags", List.of("Фентезі"), "adult", true, "language", "ja",
                "titleOriginal", "水の魔法使い", "rightsConfirmed", true,
                "description", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("text", "Хлопець, що " + "говорить із водою.")))))));
        assertThat(made.status()).as(made.body()).isEqualTo(201);
        JsonNode novel = JSON.readTree(made.body());
        assertThat(novel.path("novel").asString()).isEqualTo(slug);
        assertThat(novel.path("kind").asString()).isEqualTo("human");
        assertThat(novel.path("status").asString()).isEqualTo("ongoing");
        assertThat(novel.path("adult").asBoolean()).isTrue();
        assertThat(novel.path("tags").path(0).asString()).isEqualTo("Фентезі");
        assertThat(novel.path("language").asString()).isEqualTo("ja");
        assertThat(novel.path("titleOriginal").asString()).isEqualTo("水の魔法使い");
        assertThat(novel.path("description").path(0).path("content").path(0).path("text").asString()).startsWith("Хлопець");
        assertThat(novel.path("role").asString()).isEqualTo("owner");
        assertThat(novel.path("cover").isNull()).isTrue();

        long edition = editionOf(slug);
        JsonNode mine = read(author.browser().get("/api/studio"));
        assertThat(mine.valueStream().filter(item -> item.path("novelSlug").asString().equals(slug)).findFirst())
                .as("in «Мої переклади»").hasValueSatisfying(item -> assertThat(item.path("teamHandle").asString())
                        .isEqualTo(novel.path("team").asString()));
        JsonNode overview = read(author.browser().get("/api/studio/editions/" + edition));
        assertThat(overview.path("title").asString()).isEqualTo("Маг води");
        assertThat(overview.path("ownNovel").asBoolean()).isTrue();
        assertThat(overview.path("adult").asBoolean()).isTrue();
        assertThat(db.select(EDITION.RIGHTS_CONFIRMED_AT).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1()).isNotNull();
        assertThat(read(bearer("GET", base(novel), studio, null)).path("title").asString()).isEqualTo("Маг води");

        long tokenId = db.select(API_TOKEN.ID).from(API_TOKEN).where(API_TOKEN.SECRET_HASH.eq(sha256(studio))).fetchSingle(API_TOKEN.ID);
        assertThat(db.select(API_TOKEN_WRITE.PATH).from(API_TOKEN_WRITE).where(API_TOKEN_WRITE.TOKEN_ID.eq(tokenId)).fetch(API_TOKEN_WRITE.PATH))
                .contains("/api/v1/studio/novels");
        assertThat(events.of(edition)).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(NovelChanged.Action.CREATED);
            assertThat(event.actorId()).isEqualTo(accountId(author));
            assertThat(event.changes()).containsEntry("slug", slug).containsEntry("kind", "human");
        });
    }

    @Test
    void anOwnWorkIsByItsAuthor() {
        JsonNode work = read(bearer("POST", "/api/v1/studio/novels", studio,
                JSON.writeValueAsString(Map.of("kind", "original", "title", "Свій твір " + suffix(), "language", "ja"))));
        assertThat(work.path("kind").asString()).isEqualTo("original");
        assertThat(work.path("author").asString()).as("«Автор @нік»").isEqualTo(author.nick());
        assertThat(work.path("language").isNull()).as("an own work has no original").isTrue();
        assertThat(db.select(NOVEL.AUTHOR_ACCOUNT_ID).from(NOVEL).where(NOVEL.SLUG.eq(work.path("novel").asString())).fetchSingle().value1())
                .isEqualTo(accountId(author));
    }

    @Test
    void mistakesAreSaidInWordsAndNothingIsHalfMade() {
        String slug = "zainiata-" + suffix();
        read(bearer("POST", "/api/v1/studio/novels", studio, JSON.writeValueAsString(Map.of("kind", "human", "title", "Перша", "slug", slug))));
        String title = "Друга " + suffix();
        Response taken = bearer("POST", "/api/v1/studio/novels", studio,
                JSON.writeValueAsString(Map.of("kind", "human", "title", title, "slug", slug)));
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.body()).contains("Така адреса вже зайнята");
        assertThat(db.fetchExists(NOVEL, NOVEL.TITLE.eq(title))).as("the work is not made without its address").isFalse();

        assertThat(refused(Map.of("kind", "machine", "title", "Машинний"))).contains("свій переклад (human) чи свій твір (original)");
        assertThat(refused(Map.of("kind", "human", "title", " "))).contains("Назва новели — від 1 до 200 символів");
        assertThat(refused(Map.of("kind", "human", "title", "Тег", "tags", List.of("Вигаданий тег")))).contains("Тегу «Вигаданий тег» немає в списку");
        assertThat(refused(Map.of("kind", "human", "title", "Мова", "language", "fr"))).contains("Мова оригіналу — ja, en, zh чи ko");
        assertThat(refused(Map.of("kind", "human", "title", "Адреса", "slug", "Ж"))).contains("Адреса — латинські літери");
    }

    @Test
    void theOwnerChangesTheDataAndTheState() {
        JsonNode novel = read(bearer("POST", "/api/v1/studio/novels", studio, newNovel("human", null)));
        long edition = editionOf(novel.path("novel").asString());

        JsonNode paused = read(bearer("PATCH", base(novel) + "/settings", studio, json("status", "paused", "pausedUntil", "2027-01-15")));
        assertThat(paused.path("status").asString()).isEqualTo("paused");
        assertThat(paused.path("pausedUntil").asString()).isEqualTo("2027-01-15");
        JsonNode completed = read(bearer("PATCH", base(novel) + "/settings", studio, json("status", "completed")));
        assertThat(completed.path("status").asString()).isEqualTo("completed");
        assertThat(completed.path("pausedUntil").isNull()).as("a pause ends with it").isTrue();
        Response bogus = bearer("PATCH", base(novel) + "/settings", studio, json("status", "forgotten"));
        assertThat(bogus.status()).isEqualTo(400);
        assertThat(bogus.body()).contains("Невідомий стан перекладу");

        String slug = "nova-adresa-" + suffix();
        JsonNode renamed = read(bearer("PATCH", base(novel) + "/settings", studio,
                JSON.writeValueAsString(Map.of("title", "Нова назва", "slug", slug, "adult", true, "tags", List.of("Фентезі")))));
        assertThat(renamed.path("novel").asString()).isEqualTo(slug);
        assertThat(renamed.path("title").asString()).isEqualTo("Нова назва");
        assertThat(renamed.path("adult").asBoolean()).isTrue();
        assertThat(renamed.path("tags").path(0).asString()).isEqualTo("Фентезі");
        assertThat(renamed.path("status").asString()).as("what was not sent stays").isEqualTo("completed");
        assertThat(read(author.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString()).isEqualTo(slug);
        String other = read(bearer("POST", "/api/v1/studio/novels", studio, newNovel("human", null))).path("novel").asString();
        Response clash = bearer("PATCH", base(renamed) + "/settings", studio, json("slug", other, "title", "Не зміниться"));
        assertThat(clash.status()).isEqualTo(409);
        assertThat(read(bearer("GET", base(renamed) + "/settings", studio, null)).path("title").asString())
                .as("a refused address leaves everything as it was").isEqualTo("Нова назва");

        Person editor = Accounts.signedIn(port, mailbox);
        long teamId = db.select(EDITION.TEAM_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1();
        db.insertInto(TEAM_MEMBER).set(TEAM_MEMBER.TEAM_ID, teamId).set(TEAM_MEMBER.ACCOUNT_ID, accountId(editor))
                .set(TEAM_MEMBER.ROLE, "editor").execute();
        String theirs = token(editor, List.of("studio"));
        assertThat(read(bearer("GET", base(renamed) + "/settings", theirs, null)).path("role").asString()).isEqualTo("editor");
        assertThat(bearer("PATCH", base(renamed) + "/settings", theirs, json("status", "abandoned")).status())
                .as("the data are the owner's").isEqualTo(403);
        assertThat(bearer("GET", base(renamed) + "/settings", token(Accounts.signedIn(port, mailbox), List.of("studio")), null).status())
                .as("not in the team").isEqualTo(403);

        assertThat(events.of(edition)).extracting(NovelChanged::action).containsExactly(NovelChanged.Action.CREATED,
                NovelChanged.Action.UPDATED, NovelChanged.Action.UPDATED, NovelChanged.Action.SLUG, NovelChanged.Action.UPDATED);
        assertThat(events.of(edition).get(3).changes()).containsEntry("slug", slug);
    }

    @Test
    void theCoverIsAFileOfItsOwn() {
        JsonNode novel = read(bearer("POST", "/api/v1/studio/novels", studio, newNovel("human", null)));
        long edition = editionOf(novel.path("novel").asString());

        Response notPicture = upload(base(novel) + "/cover", studio, "cover.png", "не картинка".getBytes(StandardCharsets.UTF_8));
        assertThat(notPicture.status()).isEqualTo(400);

        Response covered = upload(base(novel) + "/cover", studio, "cover.png", Pictures.png(400, 500, Color.ORANGE));
        assertThat(covered.status()).as(covered.body()).isEqualTo(200);
        assertThat(JSON.readTree(covered.body()).path("cover").asString()).isNotBlank();
        assertThat(read(author.browser().get("/api/studio/editions/" + edition)).path("coverUrl").asString()).isNotBlank();

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(upload(base(novel) + "/cover", token(stranger, List.of("studio")), "cover.png", Pictures.png(400, 600, Color.RED)).status())
                .isEqualTo(403);

        JsonNode removed = read(bearer("DELETE", base(novel) + "/cover", studio, null));
        assertThat(removed.path("cover").isNull()).isTrue();
        assertThat(events.of(edition)).extracting(NovelChanged::action).containsExactly(NovelChanged.Action.CREATED,
                NovelChanged.Action.COVER, NovelChanged.Action.COVER);
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private String newNovel(String kind, String slug) {
        return slug == null ? JSON.writeValueAsString(Map.of("kind", kind, "title", "Твір " + suffix()))
                : JSON.writeValueAsString(Map.of("kind", kind, "title", "Твір " + suffix(), "slug", slug));
    }

    private String refused(Map<String, Object> body) {
        Response response = bearer("POST", "/api/v1/studio/novels", studio, JSON.writeValueAsString(body));
        assertThat(response.status()).as(response.body()).isEqualTo(400);
        return response.body();
    }

    private static String base(JsonNode novel) {
        return "/api/v1/studio/novels/" + novel.path("novel").asString() + "/translations/" + novel.path("team").asString();
    }

    private long editionOf(String slug) {
        return db.select(EDITION.ID).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).where(NOVEL.SLUG.eq(slug))
                .fetchSingle().value1();
    }

    private long accountId(Person person) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(person.nick())).fetchSingle().value1();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String token(Person person, List<String> scopes) {
        return read(person.browser().post("/api/me/api-tokens", json("name", "Агент", "scopes", scopes, "days", 7)))
                .path("secret").asString();
    }

    private Response bearer(String method, String path, String token, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return send(request);
    }

    private Response upload(String path, String token, String filename, byte[] content) {
        String boundary = "----novelka" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    private static Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.nullNode() : JSON.readTree(response.body());
    }
}
