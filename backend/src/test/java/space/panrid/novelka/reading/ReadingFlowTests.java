package space.panrid.novelka.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.support.Browser.json;

import java.nio.charset.StandardCharsets;
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
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class ReadingFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOVEL = """
            # Пролог
            Рьо відкрив очі й побачив **стелю**.

            # Спокійне життя
            Перше слово в новому житті.
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

    Person admin;
    String title;

    @BeforeEach
    void anAdministratorImportsANovel() {
        admin = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "admin").where(ACCOUNT.NICK.eq(admin.nick())).execute();
        title = "Маг води " + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void aGuestFindsReadsAndMovesBetweenChapters() {
        JsonNode imported = read(importNovel(admin.browser(), title, NOVEL, false));
        String slug = imported.path("slug").asString();
        assertThat(slug).startsWith("mah-vody-");
        assertThat(imported.path("chapters").asInt()).isEqualTo(3);

        Browser guest = new Browser(port);
        JsonNode page = read(guest.get("/api/novels/" + slug));
        assertThat(page.path("title").asString()).isEqualTo(title);
        assertThat(page.path("origin").asString()).isEqualTo("translation");
        assertThat(page.path("tags")).extracting(JsonNode::asString).containsExactlyInAnyOrder("перевтілення", "Фентезі");
        assertThat(page.path("edition").path("chapterCount").asInt()).isEqualTo(3);
        assertThat(page.path("edition").path("teamHandle").asString()).isEqualTo(admin.nick());
        assertThat(page.path("viewer").isNull()).isTrue();

        JsonNode chapters = read(guest.get("/api/novels/" + slug + "/chapters"));
        assertThat(chapters.path("items")).extracting(row -> row.path("title").asString())
                .containsExactly("Пролог", "Спокійне життя", "Нічний ринок");

        JsonNode second = read(guest.get("/api/novels/" + slug + "/chapters/2"));
        assertThat(second.path("title").asString()).isEqualTo("Спокійне життя");
        assertThat(second.path("previous").asInt()).isEqualTo(1);
        assertThat(second.path("next").asInt()).isEqualTo(3);
        assertThat(second.path("blocks")).extracting(block -> block.path("type").asString())
                .containsExactly("paragraph", "separator", "paragraph");
        JsonNode first = read(guest.get("/api/novels/" + slug + "/chapters/1"));
        assertThat(first.path("blocks").get(0).path("content").get(1).path("marks").get(0).asString()).isEqualTo("bold");
        assertThat(first.path("previous").isNull()).isTrue();

        assertThat(guest.get("/api/novels/" + slug + "/chapters/99").status()).isEqualTo(404);
        assertThat(guest.get("/api/novels/nothing-here").status()).isEqualTo(404);
    }

    @Test
    void homeAndCatalogShowTheNewNovel() {
        String slug = read(importNovel(admin.browser(), title, NOVEL, false)).path("slug").asString();
        Browser guest = new Browser(port);

        JsonNode home = read(guest.get("/api/home"));
        assertThat(home.path("newChapters")).anySatisfy(item -> {
            assertThat(item.path("card").path("novelSlug").asString()).isEqualTo(slug);
            assertThat(item.path("firstNumber").asInt()).isEqualTo(1);
            assertThat(item.path("lastNumber").asInt()).isEqualTo(3);
        });
        assertThat(home.path("popular")).anySatisfy(card -> assertThat(card.path("novelSlug").asString()).isEqualTo(slug));
        assertThat(home.path("continueReading")).isEmpty();

        String word = title.substring(title.length() - 6);
        assertThat(read(guest.get("/api/catalog?q=" + word)).path("items"))
                .singleElement().satisfies(card -> assertThat(card.path("novelSlug").asString()).isEqualTo(slug));
        assertThat(read(guest.get("/api/catalog?q=" + word + "&tag=фентезі")).path("items")).hasSize(1);
        assertThat(read(guest.get("/api/catalog?q=" + word + "&tag=жахи")).path("items")).isEmpty();
        assertThat(read(guest.get("/api/catalog?q=" + word + "&kind=original")).path("items")).isEmpty();
        assertThat(read(guest.get("/api/tags"))).anySatisfy(tag -> assertThat(tag.path("slug").asString()).isEqualTo("фентезі"));
    }

    @Test
    void readingRemembersThePlaceAndFillsTheLibrary() {
        String slug = read(importNovel(admin.browser(), title, NOVEL, false)).path("slug").asString();
        Person reader = Accounts.signedIn(port, mailbox);
        long editionId = read(reader.browser().get("/api/novels/" + slug)).path("edition").path("editionId").asLong();

        assertThat(reader.browser().put("/api/progress/" + editionId, json("chapterNumber", 2, "position", 0.4)).status())
                .isEqualTo(204);

        JsonNode home = read(reader.browser().get("/api/home"));
        assertThat(home.path("continueReading")).singleElement().satisfies(item -> {
            assertThat(item.path("chapterNumber").asInt()).isEqualTo(2);
            assertThat(item.path("position").asDouble()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
        });
        JsonNode library = read(reader.browser().get("/api/library?list=reading"));
        assertThat(library.path("items")).singleElement()
                .satisfies(item -> assertThat(item.path("chapterNumber").asInt()).isEqualTo(2));
        assertThat(library.path("counts").path("reading").asInt()).isEqualTo(1);

        assertThat(reader.browser().put("/api/library/" + editionId, json("list", "planned")).status()).isEqualTo(204);
        JsonNode planned = read(reader.browser().get("/api/library?list=planned"));
        assertThat(planned.path("counts").path("reading").asInt()).isZero();
        assertThat(planned.path("counts").path("planned").asInt()).isEqualTo(1);
        assertThat(read(reader.browser().get("/api/novels/" + slug)).path("viewer").path("list").asString()).isEqualTo("planned");

        assertThat(reader.browser().put("/api/progress/" + editionId, json("chapterNumber", 9, "position", 0)).status())
                .isEqualTo(404);
        assertThat(new Browser(port).put("/api/progress/" + editionId, json("chapterNumber", 1, "position", 0)).status())
                .as("guests keep progress in the browser only").isEqualTo(401);
    }

    @Test
    void adultNovelsStayHiddenUntilTheAgeIsConfirmed() {
        String slug = read(importNovel(admin.browser(), title, NOVEL, true)).path("slug").asString();
        String word = title.substring(title.length() - 6);

        Response guest = new Browser(port).get("/api/novels/" + slug);
        assertThat(guest.status()).isEqualTo(403);
        assertThat(parse(guest).path("reason").asString()).isEqualTo("adult");
        assertThat(read(new Browser(port).get("/api/catalog?q=" + word)).path("items")).isEmpty();

        Person reader = Accounts.signedIn(port, mailbox);
        reader.browser().patch("/api/me", json("adultConfirmed", true));
        assertThat(reader.browser().get("/api/novels/" + slug).status()).isEqualTo(200);
        assertThat(read(reader.browser().get("/api/catalog?q=" + word)).path("items")).hasSize(1);
    }

    @Test
    void onlyAdministratorsImportAndFilesMustBeUtf8() {
        Person reader = Accounts.signedIn(port, mailbox);
        assertThat(importNovel(reader.browser(), title, NOVEL, false).status()).isEqualTo(403);

        byte[] windows1251 = "Глава 1\nТекст".getBytes(java.nio.charset.Charset.forName("windows-1251"));
        Response response = admin.browser().upload("/api/admin/novels", Map.of("title", title), "file", "old.txt",
                "text/plain", windows1251);
        assertThat(response.status()).isEqualTo(400);
        assertThat(parse(response).path("detail").asString()).contains("UTF-8");
    }

    private static Response importNovel(Browser browser, String title, String text, boolean adult) {
        return browser.upload("/api/admin/novels",
                Map.of("title", title, "author", "Тадаші Хісахо", "description", "Рьо перевтілився у світі «Фі».",
                        "tags", "Фентезі, перевтілення", "kind", "machine", "adult", Boolean.toString(adult)),
                "file", "mag.md", "text/markdown", text.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return parse(response);
    }

    private static JsonNode parse(Response response) {
        return JSON.readTree(response.body());
    }
}
