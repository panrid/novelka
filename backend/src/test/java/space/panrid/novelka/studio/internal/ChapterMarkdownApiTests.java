package space.panrid.novelka.studio.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.support.Browser.json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.media.internal.FakeRemoteImages;
import space.panrid.novelka.platform.web.Idempotency;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Етап 22.11: a chapter's text sent as Markdown or plain text becomes the blocks the Studio's
 * import of the same file gives — pictures by link downloaded, all or none.
 */
@IntegrationTest
class ChapterMarkdownApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    FakeRemoteImages remote;

    Person owner;
    String studio;
    String slug;
    String base;
    long edition;

    @BeforeEach
    void aTranslationOfItsOwn() {
        owner = Accounts.signedIn(port, mailbox);
        var response = owner.browser().post("/api/me/api-tokens", json("name", "Скрипт", "scopes", List.of("studio"), "days", 7));
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        studio = JSON.readTree(response.body()).path("secret").asString();
        slug = "markdown-" + suffix();
        JsonNode novel = read(call("POST", "/api/v1/studio/novels", null,
                JSON.writeValueAsString(Map.of("kind", "human", "title", "Глава з Markdown", "slug", slug))));
        base = "/api/v1/studio/novels/" + slug + "/translations/" + novel.path("team").asString();
        edition = db.select(EDITION.ID).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).where(NOVEL.SLUG.eq(slug))
                .fetchSingle().value1();
    }

    @Test
    void markdownGivesTheBlocksTheImportOfTheSameFileGives() {
        String picture = FakeRemoteImages.HOST + suffix() + ".png";
        String file = """
                # Глава 1. Перший день

                Він **знав**, що ++це++ ~~не~~ *сон*, а _дійсність_.
                ---
                ![Хатина](%s)
                ## Вночі
                Далі — [посилання](https://example.com) і `код`.
                * * *
                Кінець.
                """.formatted(picture);

        var imported = owner.browser().upload("/api/studio/editions/" + edition + "/import", "file", "1.md", "text/markdown",
                file.getBytes(StandardCharsets.UTF_8));
        assertThat(imported.status()).as(imported.body()).isEqualTo(201);
        JsonNode site = read(call("GET", base + "/chapters/1", null, null));

        HttpResponse<String> put = call("PUT", base + "/chapters/2", null, markdown(null, file));
        assertThat(put.statusCode()).as(put.body()).isEqualTo(201);
        JsonNode api = read(call("GET", base + "/chapters/2", null, null)).path("draft");

        assertThat(api.path("title").asString()).isEqualTo(site.path("title").asString()).isEqualTo("Перший день");
        assertThat(withoutPictureIds(api.path("blocks"))).isEqualTo(withoutPictureIds(site.path("blocks")));
        assertThat(api.path("blocks")).extracting(block -> block.path("type").asString())
                .containsExactly("paragraph", "separator", "image", "paragraph", "paragraph", "separator", "paragraph");
        JsonNode image = api.path("blocks").path(2);
        assertThat(image.path("imageId").asLong()).as("the picture is the site's own copy").isPositive();
        assertThat(image.path("imageUrl").asString()).isNotBlank();
        assertThat(db.fetchCount(IMAGE, IMAGE.SOURCE_URL.eq(picture))).as("downloaded once by the import, once by the API").isEqualTo(2);
    }

    @Test
    void plainTextAndMarkdownGoThroughTheDraftAndPublishing() {
        read(call("PUT", base + "/chapters/1", null, text("blocks", List.of())));

        Map<String, Object> plain = new HashMap<>();
        plain.put("title", "Своя назва");
        plain.put("format", "plain");
        plain.put("text", "Глава 1. З файла\n**як є**\n***\nДалі.");
        JsonNode draft = read(call("PUT", base + "/chapters/1/draft", null, JSON.writeValueAsString(plain))).path("draft");
        assertThat(draft.path("title").asString()).as("the title field wins over the heading").isEqualTo("Своя назва");
        assertThat(draft.path("blocks")).extracting(block -> block.path("type").asString())
                .containsExactly("paragraph", "separator", "paragraph");
        assertThat(draft.path("blocks").path(0).path("content").path(0).path("text").asString()).isEqualTo("**як є**");

        read(call("POST", base + "/chapters/1/publish", null, markdown("Опубліковано", "Тепер *курсив*.")));
        JsonNode chapter = read(call("GET", base + "/chapters/1", null, null));
        assertThat(chapter.path("published").asBoolean()).isTrue();
        assertThat(chapter.path("title").asString()).isEqualTo("Опубліковано");
        assertThat(chapter.path("blocks").path(0).path("content").path(1).path("marks").path(0).asString()).isEqualTo("italic");
    }

    @Test
    void whatCannotBecomeAChapterIsRefusedAndNothingIsSaved() {
        assertRefused(text("blocks", List.of(), "text", "Абзац.", "format", "markdown"), "або blocks, або text");
        assertRefused(text("text", "Абзац."), "format");
        assertRefused(text("text", "Абзац.", "format", "html"), "Невідомий format");
        assertRefused(text("blocks", List.of(), "format", "markdown"), "format не потрібен");
        assertRefused(markdown(null, "  \n\n "), "порожній");
        assertRefused(markdown(null, ("а".repeat(999) + "\n").repeat(201)), "задовга");
        assertRefused(markdown(null, "Перша.\n# Друга глава\nТекст."), "починає нову");
        assertRefused(markdown(null, "Текст.\n![](http://example.com/a.png)"), "https://");
        assertRefused(markdown(null, "Текст.\n![](https://127.0.0.1/a.png)"), "Картинка 1 (https://127.0.0.1/a.png)");

        String good = FakeRemoteImages.HOST + suffix() + ".png";
        HttpResponse<String> missing = call("PUT", base + "/chapters/3", null,
                markdown(null, "![](%s)\nТекст.\n![](%smissing.png)".formatted(good, FakeRemoteImages.HOST)));
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("Картинка 2 (" + FakeRemoteImages.HOST + "missing.png)", "сайт відповів 404",
                "Нічого не збережено");
        assertThat(db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(edition))).isZero();
        assertThat(db.fetchCount(IMAGE, IMAGE.SOURCE_URL.eq(good))).as("the first picture is not kept either").isZero();
    }

    @Test
    void anIdempotencyKeyCoversMarkdownTooAndPicturesAreDownloadedOnce() {
        String picture = FakeRemoteImages.HOST + suffix() + ".png";
        String body = markdown("Ніч", "Абзац.\n\n![](%s)".formatted(picture));
        String key = UUID.randomUUID().toString();

        HttpResponse<String> first = call("PUT", base + "/chapters/5", key, body);
        HttpResponse<String> again = call("PUT", base + "/chapters/5", key, body);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(again.statusCode()).isEqualTo(201);
        assertThat(again.body()).isEqualTo(first.body());
        assertThat(again.headers().firstValue(Idempotency.REPLAYED)).hasValue("true");
        assertThat(remote.requested.stream().filter(picture::equals)).hasSize(1);

        HttpResponse<String> other = call("PUT", base + "/chapters/5", key, markdown("Ніч", "Інший абзац."));
        assertThat(other.statusCode()).isEqualTo(409);
    }

    private void assertRefused(String body, String message) {
        HttpResponse<String> refused = call("PUT", base + "/chapters/3", null, body);
        assertThat(refused.statusCode()).as(refused.body()).isEqualTo(400);
        assertThat(refused.body()).contains(message);
        assertThat(db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(edition))).isZero();
    }

    /** Each download is a picture of its own: the blocks are compared without whose they are. */
    private static JsonNode withoutPictureIds(JsonNode blocks) {
        JsonNode copy = blocks.deepCopy();
        for (JsonNode block : copy) {
            if (block.path("imageId").isNumber()) {
                ((ObjectNode) block).put("imageId", 0).put("imageUrl", "");
            }
        }
        return copy;
    }

    private static String markdown(String title, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("format", "markdown");
        body.put("text", text);
        return JSON.writeValueAsString(body);
    }

    private static String text(Object... fields) {
        Map<String, Object> body = new HashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            body.put((String) fields[i], fields[i + 1]);
        }
        return JSON.writeValueAsString(body);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpResponse<String> call(String method, String path, String key, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + studio)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (key != null) {
            request.header(Idempotency.HEADER, key);
        }
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode read(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
