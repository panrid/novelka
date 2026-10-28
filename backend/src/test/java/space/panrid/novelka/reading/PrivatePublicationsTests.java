package space.panrid.novelka.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.IMAGE_FILE;
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

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.BinaryResponse;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 24.1 (issue #329): приватні публікації: команда твору й запрошені читачі.
 */
@IntegrationTest
class PrivatePublicationsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person author;
    Person stranger;
    Person invited;

    @BeforeEach
    void setUp() {
        author = Accounts.signedIn(port, mailbox);
        stranger = Accounts.signedIn(port, mailbox);
        invited = Accounts.signedIn(port, mailbox);
    }

    @Test
    void anEditionCanSwitchVisibilityBetweenPublicAndPrivate() {
        String title = "Оригінальний твір " + UUID.randomUUID().toString().substring(0, 6);
        long edition = createPublicNovel(author, title);

        JsonNode overview = read(author.browser().get("/api/studio/editions/" + edition));
        assertThat(overview.path("visibility").asString()).isEqualTo("public");

        // The owner switches visibility to private
        assertThat(author.browser().put("/api/studio/editions/" + edition + "/visibility",
                json("visibility", "private")).status()).isEqualTo(200);
        assertThat(read(author.browser().get("/api/studio/editions/" + edition)).path("visibility").asString())
                .isEqualTo("private");

        // The owner switches back to public
        assertThat(author.browser().put("/api/studio/editions/" + edition + "/visibility",
                json("visibility", "public")).status()).isEqualTo(200);
        assertThat(read(author.browser().get("/api/studio/editions/" + edition)).path("visibility").asString())
                .isEqualTo("public");

        // A stranger cannot change the visibility
        assertThat(stranger.browser().put("/api/studio/editions/" + edition + "/visibility",
                json("visibility", "private")).status()).isBetween(400, 499);
    }

    @Test
    void privateNovelIsHiddenFromGuestsAndStrangers() {
        String title = "Таємна книга " + UUID.randomUUID().toString().substring(0, 6);
        long edition = createPrivateNovel(author, title);
        String slug = read(author.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        String teamHandle = read(author.browser().get("/api/studio/editions/" + edition)).path("teamHandle").asString();

        // Add chapter 1 and publish it
        int ch1 = read(author.browser().post("/api/studio/editions/" + edition + "/chapters", "{}")).path("number").asInt();
        author.browser().post(chapter(edition, ch1) + "/publish",
                chapterContent("Глава 1", null, "Секретний текст для обраних."));

        // Add chapter 2 as draft
        int ch2 = read(author.browser().post("/api/studio/editions/" + edition + "/chapters", "{}")).path("number").asInt();
        author.browser().put(chapter(edition, ch2) + "/draft",
                chapterContent("Глава 2", null, "Чернетка розділу 2"));

        Browser guest = new Browser(port);
        String searchWord = title.substring(title.length() - 6);

        // 1. Guest checks
        assertThat(guest.get("/api/novels/" + slug).status()).as("novel page returns 404 to guest").isEqualTo(404);
        assertThat(guest.get("/api/novels/" + slug + "/chapters").status()).as("chapters list returns 404").isEqualTo(404);
        assertThat(guest.get("/api/novels/" + slug + "/chapters/1").status()).as("chapter 1 returns 404").isEqualTo(404);
        assertThat(guest.get("/api/novels/" + slug + "/epub").status()).as("epub download returns 401 or 404 to guest").isIn(401, 404);
        assertThat(guest.get("/api/public/novels/" + slug).status()).as("public API returns 404").isEqualTo(404);

        // Catalog, hints, home, sitemap
        assertThat(read(guest.get("/api/catalog?q=" + searchWord)).path("items")).as("omitted from catalog").isEmpty();
        assertThat(read(guest.get("/api/search/hints?q=" + searchWord)).path("novels")).as("omitted from hints").isEmpty();
        JsonNode home = read(guest.get("/api/home"));
        assertThat(home.path("newChapters")).noneSatisfy(item ->
                assertThat(item.path("card").path("novelSlug").asString()).isEqualTo(slug));
        assertThat(home.path("popular")).noneSatisfy(card ->
                assertThat(card.path("novelSlug").asString()).isEqualTo(slug));

        String sitemap = guest.get("/sitemap.xml").body();
        assertThat(sitemap).as("sitemap does not disclose private novel").doesNotContain(slug);

        // Team and author profile
        JsonNode team = read(guest.get("/api/teams/" + teamHandle));
        assertThat(team.path("editions")).noneSatisfy(ed ->
                assertThat(ed.path("novelSlug").asString()).isEqualTo(slug));
        JsonNode profile = read(guest.get("/api/users/" + author.nick()));
        assertThat(profile.path("works")).noneSatisfy(w ->
                assertThat(w.path("novelSlug").asString()).isEqualTo(slug));

        // 2. Stranger checks (authenticated, but neither team nor invited reader)
        assertThat(stranger.browser().get("/api/novels/" + slug).status()).as("404 for stranger").isEqualTo(404);
        assertThat(stranger.browser().get("/api/novels/" + slug + "/chapters/1").status()).isEqualTo(404);
        assertThat(stranger.browser().download("/api/novels/" + slug + "/epub").status()).isEqualTo(404);
        assertThat(stranger.browser().get("/api/comments/editions/" + edition).status()).isEqualTo(404);
        assertThat(stranger.browser().post("/api/comments/editions/" + edition, json("body", "Чужий коментар")).status())
                .isEqualTo(404);
        assertThat(stranger.browser().post("/api/ratings/editions/" + edition, json("stars", 5)).status()).isEqualTo(404);
    }

    @Test
    void invitedReadersCanReadPublishedChaptersAndDownloadEpub() {
        String title = "Приватний роман " + UUID.randomUUID().toString().substring(0, 6);
        long edition = createPrivateNovel(author, title);
        String slug = read(author.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();

        // Chapter 1 published
        int ch1 = read(author.browser().post("/api/studio/editions/" + edition + "/chapters", "{}")).path("number").asInt();
        author.browser().post(chapter(edition, ch1) + "/publish",
                chapterContent("Перший розділ", null, "Текст першого розділу."));

        // Chapter 2 draft
        int ch2 = read(author.browser().post("/api/studio/editions/" + edition + "/chapters", "{}")).path("number").asInt();
        author.browser().put(chapter(edition, ch2) + "/draft",
                chapterContent("Другий розділ", null, "Чернетка другого розділу."));

        // Author invites reader
        assertThat(author.browser().post("/api/studio/editions/" + edition + "/readers",
                json("nick", invited.nick())).status()).isEqualTo(204);

        // Reader list in studio
        JsonNode readers = read(author.browser().get("/api/studio/editions/" + edition + "/readers"));
        assertThat(readers).singleElement().satisfies(row ->
                assertThat(row.path("nick").asString()).isEqualTo(invited.nick()));

        // Stranger cannot view readers list
        assertThat(stranger.browser().get("/api/studio/editions/" + edition + "/readers").status()).isEqualTo(404);

        // Invited reader can now access the novel
        JsonNode page = read(invited.browser().get("/api/novels/" + slug));
        assertThat(page.path("title").asString()).isEqualTo(title);
        assertThat(page.path("viewer").path("teamRole").isNull()).as("invited reader is not team").isTrue();

        // Invited reader sees only published chapter 1, not draft chapter 2
        JsonNode chapters = read(invited.browser().get("/api/novels/" + slug + "/chapters"));
        assertThat(chapters.path("items")).extracting(row -> row.path("title").asString())
                .containsExactly("Перший розділ");

        JsonNode chapter1 = read(invited.browser().get("/api/novels/" + slug + "/chapters/1"));
        assertThat(chapter1.path("title").asString()).isEqualTo("Перший розділ");
        assertThat(chapter1.path("blocks").get(0).path("content").get(0).path("text").asString())
                .isEqualTo("Текст першого розділу.");

        // Draft chapter 2 is inaccessible to invited reader
        assertThat(invited.browser().get("/api/novels/" + slug + "/chapters/2").status()).isEqualTo(404);

        // Invited reader can download EPUB
        BinaryResponse epub = invited.browser().download("/api/novels/" + slug + "/epub");
        assertThat(epub.status()).isEqualTo(200);
        assertThat(epub.contentType()).isEqualTo("application/epub+zip");

        // Studio remains forbidden to invited reader
        assertThat(invited.browser().get("/api/studio/editions/" + edition).status()).isEqualTo(404);

        // Revoke reader access
        assertThat(author.browser().delete("/api/studio/editions/" + edition + "/readers/" + invited.nick()).status())
                .isEqualTo(204);

        // Immediate 404 upon revocation
        assertThat(invited.browser().get("/api/novels/" + slug).status()).as("404 after revocation").isEqualTo(404);
        assertThat(invited.browser().get("/api/novels/" + slug + "/chapters/1").status()).isEqualTo(404);
        assertThat(invited.browser().download("/api/novels/" + slug + "/epub").status()).isEqualTo(404);
    }

    @Test
    void mediaGateProtectsCoverAndDraftPictures() {
        String title = "Ілюстрований твір " + UUID.randomUUID().toString().substring(0, 6);
        long edition = createPrivateNovel(author, title);

        // Author uploads a cover image
        byte[] coverBytes = Pictures.png(600, 900, Color.BLUE);
        long coverId = read(author.browser().upload("/api/media/images", Map.of("kind", "cover"), "file", "cover.png",
                "image/png", coverBytes)).path("id").asLong();

        // Set edition cover
        JsonNode coverResp = read(author.browser().put("/api/studio/editions/" + edition + "/cover",
                json("imageId", coverId)));
        String coverUrl = coverResp.path("coverUrl").asString();
        assertThat(coverUrl).startsWith("/media/");

        // Author uploads a draft picture
        byte[] draftImgBytes = Pictures.png(400, 400, Color.GREEN);
        long draftImgId = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"), "file", "draft.png",
                "image/png", draftImgBytes)).path("id").asLong();

        int ch1 = read(author.browser().post("/api/studio/editions/" + edition + "/chapters", "{}")).path("number").asInt();
        // Save image in draft only (not published)
        author.browser().put(chapter(edition, ch1) + "/draft", """
                {"title":"Глава з картинкою","baseRevisionId":null,"blocks":[{"id":"b1","type":"image","imageId":%d}]}"""
                .formatted(draftImgId));

        String draftKey = db.select(IMAGE_FILE.KEY).from(IMAGE_FILE).where(IMAGE_FILE.IMAGE_ID.eq(draftImgId))
                .fetchAny(IMAGE_FILE.KEY);
        assertThat(draftKey).isNotNull();
        String draftUrl = "/media/" + draftKey;

        Browser guest = new Browser(port);

        // Guest requesting cover -> 404 + Cache-Control: private, no-store
        HttpResponse<byte[]> guestCover = guest.fetch(coverUrl);
        assertThat(guestCover.statusCode()).isEqualTo(404);
        assertThat(guestCover.headers().firstValue("Cache-Control").orElse(""))
                .contains("private").contains("no-store");

        // Stranger requesting cover -> 404 + Cache-Control: private, no-store
        HttpResponse<byte[]> strangerCover = stranger.browser().fetch(coverUrl);
        assertThat(strangerCover.statusCode()).isEqualTo(404);
        assertThat(strangerCover.headers().firstValue("Cache-Control").orElse(""))
                .contains("private").contains("no-store");

        // Team (author) requesting cover -> 200 + Cache-Control: private, no-cache
        HttpResponse<byte[]> authorCover = author.browser().fetch(coverUrl);
        assertThat(authorCover.statusCode()).isEqualTo(200);
        assertThat(authorCover.headers().firstValue("Cache-Control").orElse(""))
                .contains("private");

        // Invite reader -> invited reader can access cover
        author.browser().post("/api/studio/editions/" + edition + "/readers", json("nick", invited.nick()));
        HttpResponse<byte[]> invitedCover = invited.browser().fetch(coverUrl);
        assertThat(invitedCover.statusCode()).isEqualTo(200);

        // Draft picture:
        // Guest -> 404
        assertThat(guest.fetch(draftUrl).statusCode()).isEqualTo(404);
        // Stranger -> 404
        assertThat(stranger.browser().fetch(draftUrl).statusCode()).isEqualTo(404);
        // Invited reader -> 404 (draft image is not published!)
        assertThat(invited.browser().fetch(draftUrl).statusCode()).isEqualTo(404);
        // Author (team) -> 200
        assertThat(author.browser().fetch(draftUrl).statusCode()).isEqualTo(200);
    }

    @Test
    void oneTranslationTokenCannotAccessPrivatePublication() {
        String title = "Приватний роман токена " + UUID.randomUUID().toString().substring(0, 6);
        long privateEdition = createPrivateNovel(author, title);
        String slug = read(author.browser().get("/api/studio/editions/" + privateEdition)).path("novelSlug").asString();

        // Create another translation
        JsonNode other = read(author.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Інший публічний переклад %s","description":[],"tags":[],"language":"en"}"""
                .formatted(UUID.randomUUID().toString().substring(0, 6))));
        long otherEdition = other.path("editionId").asLong();

        // Author creates a token limited to the other translation
        String token = read(author.browser().post("/api/me/api-tokens",
                json("name", "Бот", "scopes", List.of("read", "studio"), "days", 7, "edition", otherEdition)))
                .path("secret").asString();

        // Accessing the private novel with this token fails
        Response response = bearer("GET", "/api/novels/" + slug, token);
        assertThat(response.status()).isIn(403, 404);

        Response studioResp = bearer("GET", "/api/studio/editions/" + privateEdition, token);
        assertThat(studioResp.status()).isBetween(400, 499);
    }

    private long createPublicNovel(Person person, String title) {
        long edition = read(person.browser().post("/api/studio/editions", """
                {"kind":"original","title":"%s","description":[{"id":"d1","type":"paragraph","content":[{"text":"Опис.","marks":[]}]}],
                 "tags":["Фентезі"]}""".formatted(title))).path("editionId").asLong();
        assertThat(person.browser().put("/api/studio/editions/" + edition + "/visibility", json("visibility", "public")).status()).isEqualTo(200);
        return edition;
    }

    private long createPrivateNovel(Person person, String title) {
        long edition = createPublicNovel(person, title);
        person.browser().put("/api/studio/editions/" + edition + "/visibility", json("visibility", "private"));
        return edition;
    }

    private static String chapter(long edition, int number) {
        return "/api/studio/editions/" + edition + "/chapters/" + number;
    }

    private static String chapterContent(String title, Long baseRevisionId, String paragraph) {
        return """
                {"title":"%s","baseRevisionId":%s,"blocks":[{"id":"b1","type":"paragraph","content":[{"text":"%s","marks":[]}]}]}"""
                .formatted(title, baseRevisionId == null ? "null" : baseRevisionId.toString(), paragraph);
    }

    private Response bearer(String method, String path, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return parse(response);
    }

    private static JsonNode parse(Response response) {
        return JSON.readTree(response.body());
    }
}
