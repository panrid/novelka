package space.panrid.novelka.reading;

import static org.assertj.core.api.Assertions.assertThat;

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
    void aNovelAndItsChaptersAreReadableWithoutTheAppAndListedInTheSitemap() {
        Person translator = Accounts.signedIn(port, mailbox);
        String slug = publish(translator, "Ліхтарі над ринком " + translator.nick(), false);
        Browser google = new Browser(port);

        Response novel = google.get("/n/" + slug);
        assertThat(novel.status()).isEqualTo(200);
        assertThat(novel.body())
                .contains("<title>Ліхтарі над ринком " + translator.nick() + " — читати українською | Новелка</title>")
                .contains("<meta name=\"description\" content=\"Дівчина &lt; рятує &amp; місто від &quot;тіні&quot;.\">")
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
        assertThat(google.get("/robots.txt").body()).contains("Sitemap: http://127.0.0.1:5173/sitemap.xml").contains("Disallow: /studio");
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

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
