package space.panrid.novelka.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.support.Browser.json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.platform.security.ApiRateLimits;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Етап 22.2: the public reading API, {@code /api/v1} — what the site shows, keyed pages, ETags, limits. */
@IntegrationTest
class PublicApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String NOVEL = """
            # Пролог
            Рьо відкрив очі й побачив **стелю**.

            # Спокійне життя
            Перше слово.
            ---
            Друге слово.

            # Нічний ринок
            Ліхтарі спалахнули.
            """;

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    ApiRateLimits limits;

    Person admin;
    String word;

    @BeforeEach
    void anAdministratorImportsNovels() {
        limits.forget();
        admin = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "admin").where(ACCOUNT.NICK.eq(admin.nick())).execute();
        word = UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void aGuestReadsNovelsChaptersVolumesAndTags() {
        String slug = importNovel("Зоряний шлях " + word, false);

        JsonNode catalog = ok(get("/api/v1/catalog?q=" + word, null, null));
        assertThat(catalog.path("items")).singleElement().satisfies(item -> {
            assertThat(item.path("novel").asString()).isEqualTo(slug);
            assertThat(item.path("team").asString()).isEqualTo(admin.nick());
            assertThat(item.path("chapters").asInt()).isEqualTo(3);
            assertThat(item.has("editionId")).as("no internal ids").isFalse();
        });
        assertThat(catalog.path("next").isNull()).isTrue();

        JsonNode novel = ok(get("/api/v1/novels/" + slug, null, null));
        assertThat(fields(novel)).containsExactlyInAnyOrder("slug", "title", "author", "origin", "language", "description", "tags",
                "adult", "translations");
        assertThat(novel.path("title").asString()).isEqualTo("Зоряний шлях " + word);
        assertThat(novel.path("translations")).singleElement()
                .satisfies(t -> assertThat(t.path("team").asString()).isEqualTo(admin.nick()));

        String base = "/api/v1/novels/" + slug + "/translations/" + admin.nick();
        JsonNode first = ok(get(base + "/chapters?limit=2", null, null));
        assertThat(first.path("items")).extracting(row -> row.path("title").asString()).containsExactly("Пролог", "Спокійне життя");
        assertThat(first.path("next").asString()).isEqualTo("2");
        JsonNode rest = ok(get(base + "/chapters?limit=2&after=2", null, null));
        assertThat(rest.path("items")).extracting(row -> row.path("number").asInt()).containsExactly(3);
        assertThat(rest.path("next").isNull()).isTrue();

        JsonNode chapter = ok(get(base + "/chapters/2", null, null));
        assertThat(fields(chapter)).containsExactlyInAnyOrder("novel", "team", "number", "label", "title", "volume", "blocks",
                "previous", "next");
        assertThat(chapter.path("blocks")).extracting(block -> block.path("type").asString())
                .containsExactly("paragraph", "separator", "paragraph");
        assertThat(chapter.path("previous").asInt()).isEqualTo(1);
        assertThat(chapter.path("next").asInt()).isEqualTo(3);
        assertThat(ok(get(base + "/chapters/1", null, null)).path("blocks").get(0).path("content").get(1).path("marks").get(0).asString())
                .isEqualTo("bold");

        assertThat(get(base + "/chapters/99", null, null).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/novels/" + slug + "/translations/nobody-here/chapters", null, null).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/novels/nothing-here", null, null).statusCode()).isEqualTo(404);
        assertThat(ok(get(base + "/volumes", null, null))).isEmpty();
        assertThat(ok(get(base + "/who?chapter=2&q=" + enc("Рьо"), null, null)).isArray()).isTrue();
        assertThat(ok(get("/api/v1/tags", null, null))).anySatisfy(tag -> assertThat(tag.path("slug").asString()).isEqualTo("фентезі"));
    }

    @Test
    void theCatalogIsReadByKey() {
        String one = importNovel("Перша " + word, false);
        String two = importNovel("Друга " + word, false);
        JsonNode page = ok(get("/api/v1/catalog?q=" + word + "&limit=1", null, null));
        assertThat(page.path("items")).hasSize(1);
        String next = page.path("next").asString();
        assertThat(next).isNotBlank();
        List<String> seen = new ArrayList<>(List.of(page.path("items").get(0).path("novel").asString()));
        JsonNode after = ok(get("/api/v1/catalog?q=" + word + "&limit=1&after=" + enc(next), null, null));
        seen.add(after.path("items").get(0).path("novel").asString());
        assertThat(seen).containsExactlyInAnyOrder(one, two);
        assertThat(after.path("next").isNull()).isTrue();
        assertThat(get("/api/v1/catalog?after=" + enc("not a cursor"), null, null).statusCode()).isEqualTo(400);
    }

    @Test
    void adultHiddenAndUnpublishedAreAsOnTheSite() {
        String adult = importNovel("Доросла " + word, true);
        HttpResponse<String> guest = get("/api/v1/novels/" + adult, null, null);
        assertThat(guest.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(guest.body()).path("reason").asString()).isEqualTo("adult");
        assertThat(ok(get("/api/v1/catalog?q=" + word, null, null)).path("items")).isEmpty();

        Person grown = Accounts.signedIn(port, mailbox);
        grown.browser().patch("/api/me", json("adultConfirmed", true));
        String token = token(grown, List.of("read"));
        assertThat(get("/api/v1/novels/" + adult, token, null).statusCode()).isEqualTo(200);
        assertThat(ok(get("/api/v1/catalog?q=" + word, token, null)).path("items")).hasSize(1);
        // A token without «read» does not read even what is public.
        assertThat(get("/api/v1/catalog?q=" + word, token(grown, List.of("library")), null).statusCode()).isEqualTo(403);

        String shown = importNovel("Видима " + word, false);
        long editionId = editionOf(shown);
        String base = "/api/v1/novels/" + shown + "/translations/" + admin.nick();
        db.update(CHAPTER).setNull(CHAPTER.PUBLISHED_REVISION_ID)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(3)).execute();
        assertThat(get(base + "/chapters/3", null, null).statusCode()).isEqualTo(404);
        assertThat(ok(get(base + "/chapters", null, null)).path("items")).extracting(row -> row.path("number").asInt())
                .containsExactly(1, 2);

        db.update(EDITION).set(EDITION.HIDDEN_AT, OffsetDateTime.now()).where(EDITION.ID.eq(editionId)).execute();
        assertThat(get("/api/v1/novels/" + shown, null, null).statusCode()).isEqualTo(404);
        assertThat(get(base + "/chapters/1", null, null).statusCode()).isEqualTo(404);
        assertThat(get(base + "/chapters/1", token, null).statusCode()).isEqualTo(404);
        assertThat(ok(get("/api/v1/catalog?q=" + word, token, null)).path("items"))
                .noneSatisfy(item -> assertThat(item.path("novel").asString()).isEqualTo(shown));
    }

    @Test
    void answersCarryAnETagAndMayBeCachedForAMinute() {
        String slug = importNovel("Кешована " + word, false);
        String path = "/api/v1/novels/" + slug + "/translations/" + admin.nick() + "/chapters/1";
        HttpResponse<String> first = get(path, null, null);
        assertThat(first.statusCode()).isEqualTo(200);
        String etag = first.headers().firstValue("ETag").orElseThrow();
        assertThat(first.headers().firstValue("Cache-Control").orElseThrow()).contains("max-age=60").contains("public");

        HttpResponse<String> again = get(path, null, etag);
        assertThat(again.statusCode()).isEqualTo(304);
        assertThat(again.body()).isEmpty();
        assertThat(get(path, null, "\"something else\"").statusCode()).isEqualTo(200);

        // What depends on who asks is kept only by that one.
        Person reader = Accounts.signedIn(port, mailbox);
        HttpResponse<String> mine = get(path, token(reader, List.of("read")), null);
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.headers().firstValue("Cache-Control").orElseThrow()).contains("private");
    }

    @Test
    void tooManyRequestsWaitTheirTurn() {
        String token = token(Accounts.signedIn(port, mailbox), List.of("read"));
        limits.forget();
        for (int i = 0; i < 40; i++) {
            assertThat(get("/api/v1/tags", null, null).statusCode()).isEqualTo(200);
        }
        HttpResponse<String> refused = get("/api/v1/tags", null, null);
        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(Integer.parseInt(refused.headers().firstValue("Retry-After").orElseThrow())).isBetween(1, 60);
        assertThat(refused.body()).contains("Забагато запитів");

        // A token counts by itself, not by the address.
        for (int i = 0; i < 40; i++) {
            assertThat(get("/api/v1/tags", token, null).statusCode()).isEqualTo(200);
        }
        assertThat(get("/api/v1/tags", token, null).statusCode()).isEqualTo(429);
        limits.forget();
        assertThat(get("/api/v1/tags", null, null).statusCode()).isEqualTo(200);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private String importNovel(String title, boolean adult) {
        JsonNode imported = JSON.readTree(admin.browser().upload("/api/admin/novels",
                Map.of("title", title, "author", "Тадаші Хісахо", "description", "Рьо перевтілився.",
                        "tags", "Фентезі", "kind", "machine", "adult", Boolean.toString(adult)),
                "file", "n.md", "text/markdown", NOVEL.getBytes(StandardCharsets.UTF_8)).body());
        return imported.path("slug").asString();
    }

    private long editionOf(String slug) {
        return JSON.readTree(new Browser(port).get("/api/novels/" + slug).body()).path("edition").path("editionId").asLong();
    }

    private static String token(Person person, List<String> scopes) {
        return JSON.readTree(person.browser().post("/api/me/api-tokens", json("name", "Читалка", "scopes", scopes, "days", 7)).body())
                .path("secret").asString();
    }

    private HttpResponse<String> get(String path, String token, String ifNoneMatch) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json").GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (ifNoneMatch != null) {
            request.header("If-None-Match", ifNoneMatch);
        }
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static List<String> fields(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }

    private static String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
