package space.panrid.novelka.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

/** What Google and link previews see without running the app (етап 16). */
@IntegrationTest
class SearchPagesTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    private String publish(Person translator, String title, boolean adult) {
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"%s","author":"Ірина Світанок","adult":%s,
                 "description":[{"id":"d1","type":"paragraph","content":[{"text":"Дівчина < рятує & місто від \\"тіні\\".","marks":[]}]}]}"""
                .formatted(title, adult))).path("editionId").asLong();
        for (int number = 1; number <= 2; number++) {
            translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
            read(translator.browser().post("/api/studio/editions/" + edition + "/chapters/" + number + "/publish", """
                    {"title":"Світанок %d","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Ліхтар загорівся вдруге.","marks":[]}]}]}"""
                    .formatted(number)));
        }
        return read(translator.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
    }

    @Test
    void otherNamesAndTheOriginalsStateShowAndAreSearchedByEverywhere() {
        Person translator = Accounts.signedIn(port, mailbox);
        String suffix = translator.nick().substring(translator.nick().length() - 6);
        String slug = publish(translator, "Маг води " + suffix, false);
        long edition = read(translator.browser().get("/api/studio")).valueStream()
                .filter(item -> item.path("novelSlug").asString().equals(slug)).findFirst().orElseThrow().path("editionId").asLong();
        String english = "Water Magician " + suffix;
        JsonNode saved = read(translator.browser().patch("/api/studio/editions/" + edition, """
                {"status":"paused","pausedUntil":"2026-12-01","facts":{"titleEnglish":"%s","titleOriginal":"水属性の魔法使い",
                 "altTitles":["Mizu Zokusei %s","  ","Водяний маг"],"sourceStatus":"ongoing","sourceChapterCount":822}}"""
                .formatted(english, suffix)));
        assertThat(saved.path("pausedUntil").asString()).isEqualTo("2026-12-01");
        assertThat(saved.path("facts").path("altTitles")).extracting(JsonNode::asString)
                .containsExactly("Mizu Zokusei " + suffix, "Водяний маг");

        Browser guest = new Browser(port);
        JsonNode page = read(guest.get("/api/novels/" + slug));
        assertThat(page.path("facts").path("titleEnglish").asString()).isEqualTo(english);
        assertThat(page.path("facts").path("sourceChapterCount").asInt()).isEqualTo(822);
        assertThat(page.path("edition").path("status").asString()).isEqualTo("paused");
        assertThat(page.path("edition").path("pausedUntil").asString()).isEqualTo("2026-12-01");
        for (String query : List.of("water magician " + suffix, "Mizu Zokusei " + suffix)) {
            assertThat(read(guest.get("/api/catalog?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8))).path("items"))
                    .as(query).extracting(card -> card.path("novelSlug").asString()).containsExactly(slug);
        }
        assertThat(read(guest.get("/api/catalog?q=" + URLEncoder.encode("Маг води " + suffix, StandardCharsets.UTF_8)))
                .path("items").path(0).path("sourceChapters").asInt()).isEqualTo(822);

        String html = guest.get("/n/" + slug).body();
        assertThat(html).contains("<title>Маг води " + suffix + " (" + english + ") — читати українською безкоштовно | Новелка</title>")
                .contains("\"alternateName\":[\"" + english + "\",\"水属性の魔法使い\"")
                .contains("Інші назви: " + english + " · 水属性の魔法使い");

        JsonNode resumed = read(translator.browser().patch("/api/studio/editions/" + edition, """
                {"status":"ongoing","facts":{"titleEnglish":"","sourceChapterCount":0}}"""));
        assertThat(resumed.path("pausedUntil").isNull()).as("the date belongs to the pause").isTrue();
        assertThat(resumed.path("facts").path("titleEnglish").isNull()).isTrue();
        assertThat(resumed.path("facts").path("sourceChapterCount").isNull()).isTrue();
        assertThat(translator.browser().patch("/api/studio/editions/" + edition, """
                {"facts":{"sourceStatus":"maybe"}}""").status()).isEqualTo(400);
    }

    @Test
    void aNovelAndItsChaptersAreReadableWithoutTheAppAndListedInTheSitemap() {
        Person translator = Accounts.signedIn(port, mailbox);
        String slug = publish(translator, "Ліхтарі над ринком " + translator.nick(), false);
        Browser google = new Browser(port);

        Response novel = google.get("/n/" + slug);
        assertThat(novel.status()).isEqualTo(200);
        assertThat(novel.body())
                .contains("<title>Ліхтарі над ринком " + translator.nick() + " — читати українською безкоштовно | Новелка</title>")
                .contains("<meta name=\"description\" content=\"Читайте українською безкоштовно. Дівчина &lt; рятує &amp; місто від &quot;тіні&quot;.\">")
                .contains("rel=\"canonical\" href=\"http://127.0.0.1:5173/n/" + slug + "\"")
                .contains("\"@type\":\"Book\"")
                .contains("<a href=\"/n/" + slug + "/2\">2. Світанок 2</a>")
                .doesNotContain("noindex");

        Response chapter = google.get("/n/" + slug + "/2");
        assertThat(chapter.body()).contains("<h1>2. Світанок 2</h1>").contains("<p>Ліхтар загорівся вдруге.</p>")
                .contains("rel=\"prev\" href=\"/n/" + slug + "/1\"");
        assertThat(google.get("/n/" + slug + "/9").status()).isEqualTo(404);
        assertThat(google.get("/n/no-such-novel").status()).isEqualTo(404);

        String sitemap = google.get("/sitemap.xml").body();
        assertThat(sitemap).contains("<loc>http://127.0.0.1:5173/n/" + slug + "</loc>")
                .contains("<loc>http://127.0.0.1:5173/n/" + slug + "/2</loc>");
        assertThat(google.get("/robots.txt").body()).contains("Sitemap: http://127.0.0.1:5173/sitemap.xml").contains("Disallow: /studio")
                .as("the app draws the public pages from these, Google must be able to read them")
                .contains("Allow: /api/novels/").contains("Allow: /api/me$").contains("Disallow: /api/");
    }

    @Test
    void adultTranslationsStayOutOfSearch() {
        Person translator = Accounts.signedIn(port, mailbox);
        String slug = publish(translator, "Нічні ліхтарі " + translator.nick(), true);
        Response page = new Browser(port).get("/n/" + slug);
        assertThat(page.body()).contains("<meta name=\"robots\" content=\"noindex\">").doesNotContain("Дівчина");
        assertThat(new Browser(port).get("/n/" + slug + "/1").body()).doesNotContain("Ліхтар загорівся");
        assertThat(new Browser(port).get("/sitemap.xml").body()).doesNotContain("/n/" + slug);
    }

    @Test
    void aNovelGetsABetterAddressAndTheOldOneStillLeadsToIt() {
        Person translator = Accounts.signedIn(port, mailbox);
        String old = publish(translator, "Володар водної стихії " + translator.nick(), false);
        long edition = read(translator.browser().get("/api/studio")).path(0).path("editionId").asLong();
        String wanted = "mag-vody-" + translator.nick().toLowerCase().replaceAll("[^a-z0-9]", "");

        assertThat(translator.browser().put("/api/studio/editions/" + edition + "/slug", json("slug", "Маг води")).status()).isEqualTo(400);
        JsonNode changed = read(translator.browser().put("/api/studio/editions/" + edition + "/slug", json("slug", wanted)));
        assertThat(changed.path("slug").asString()).isEqualTo(wanted);

        Browser reader = new Browser(port);
        assertThat(read(reader.get("/api/novels/" + old)).path("slug").asString()).as("the old address still finds it").isEqualTo(wanted);
        assertThat(read(reader.get("/api/novels/" + old + "/chapters/2")).path("novelSlug").asString()).isEqualTo(wanted);
        Response moved = reader.get("/n/" + old + "/2");
        assertThat(moved.status()).as("search engines move for good").isEqualTo(301);
        assertThat(google(reader, "/sitemap.xml")).contains("/n/" + wanted + "</loc>").doesNotContain("/n/" + old + "<");

        Person other = Accounts.signedIn(port, mailbox);
        String theirs = publish(other, "Інша новела " + other.nick(), false);
        long their = read(other.browser().get("/api/studio")).path(0).path("editionId").asLong();
        assertThat(other.browser().put("/api/studio/editions/" + their + "/slug", json("slug", old)).status())
                .as("an old address stays with its novel").isEqualTo(409);
        assertThat(theirs).isNotEqualTo(old);
    }

    private static String google(Browser browser, String path) {
        return browser.get(path).body();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
