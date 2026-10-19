package space.panrid.novelka.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.EDITION;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
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

/** Full-text search with trigrams (етап 21.10): typos, word forms, chapter titles, and nothing hidden leaks. */
@IntegrationTest
class SearchTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    private record Published(long edition, String slug) {
    }

    private Published publish(Person translator, String title, boolean adult, String... chapterTitles) {
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"%s","author":"Ірина Світанок","adult":%s,
                 "description":[{"id":"d1","type":"paragraph","content":[{"text":"Опис.","marks":[]}]}]}"""
                .formatted(title, adult))).path("editionId").asLong();
        for (int number = 1; number <= chapterTitles.length; number++) {
            translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
            read(translator.browser().post("/api/studio/editions/" + edition + "/chapters/" + number + "/publish", """
                    {"title":"%s","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Ліхтар загорівся.","marks":[]}]}]}"""
                    .formatted(chapterTitles[number - 1])));
        }
        return new Published(edition, read(translator.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString());
    }

    /** A word no other test's novel has, so each search here finds only this test's novels. */
    private static String unique() {
        return "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }

    private List<String> found(Browser browser, String query, String sort) {
        return read(browser.get("/api/catalog?sort=" + sort + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8))).path("items")
                .valueStream().map(card -> card.path("novelSlug").asString()).toList();
    }

    @Test
    void typosWordFormsAndAnyOrderOfWordsFindTheNovel() {
        Person translator = Accounts.signedIn(port, mailbox);
        String word = unique();
        String sorceress = publish(translator, "Чарівниця північного вітру " + word, false, "Початок").slug();
        String magic = publish(translator, "Магія вогню " + word, false, "Початок").slug();
        Browser guest = new Browser(port);

        assertThat(found(guest, "чаривниця " + word, "relevance")).as("a typo").containsExactly(sorceress);
        assertThat(found(guest, "магії " + word, "relevance")).as("another word form").containsExactly(magic);
        assertThat(found(guest, word + " вогню магія", "relevance")).as("words in any order").containsExactly(magic);
        assertThat(found(guest, "МАГІЯ " + word.toUpperCase(java.util.Locale.ROOT), "popular")).as("any case").containsExactly(magic);
        assertThat(found(guest, "Ірина " + word, "relevance")).as("the author").containsExactlyInAnyOrder(sorceress, magic);
        assertThat(found(guest, "дракон " + word, "relevance")).as("every word has to be found").isEmpty();
        assertThat(found(guest, "!!!", "relevance")).as("no words, nothing found").isEmpty();
    }

    @Test
    void chapterTitlesAreSearchedButTitlesComeFirst() {
        Person translator = Accounts.signedIn(port, mailbox);
        String word = unique();
        Published lighthouse = publish(translator, "Маяк " + word, false, "Початок");
        Published amber = publish(translator, "Бурштинове море", false, "Туман", "Таємниця старого маяка " + word);
        Browser guest = new Browser(port);

        assertThat(found(guest, "маяк " + word, "relevance")).as("the title first, then the chapter")
                .containsExactly(lighthouse.slug(), amber.slug());

        JsonNode hints = read(guest.get("/api/search/hints?q=" + URLEncoder.encode("старий маяк " + word, StandardCharsets.UTF_8)));
        assertThat(hints.path("chapters")).singleElement().satisfies(chapter -> {
            assertThat(chapter.path("novelSlug").asString()).isEqualTo(amber.slug());
            assertThat(chapter.path("novelTitle").asString()).isEqualTo("Бурштинове море");
            assertThat(chapter.path("number").asInt()).isEqualTo(2);
            assertThat(chapter.path("title").asString()).isEqualTo("Таємниця старого маяка " + word);
            assertThat(chapter.path("teamHandle").asString()).isEqualTo(translator.nick());
        });
        assertThat(hints.path("novels")).extracting(card -> card.path("novelSlug").asString()).containsExactly(amber.slug());
        assertThat(read(guest.get("/api/search/hints?q=ма")).path("chapters")).as("too short for chapters").isEmpty();
    }

    @Test
    void hiddenAndAdultTranslationsDoNotLeak() {
        Person translator = Accounts.signedIn(port, mailbox);
        String word = unique();
        Published hidden = publish(translator, "Прихована вежа " + word, false, "Вежа " + word);
        Published adult = publish(translator, "Нічна вежа " + word, true, "Ніч " + word);
        db.update(EDITION).set(EDITION.HIDDEN_AT, OffsetDateTime.now()).where(EDITION.ID.eq(hidden.edition())).execute();
        Browser guest = new Browser(port);

        assertThat(found(guest, "вежа " + word, "relevance")).isEmpty();
        assertThat(found(guest, word, "popular")).isEmpty();
        JsonNode hints = read(guest.get("/api/search/hints?q=" + word));
        assertThat(hints.path("novels")).isEmpty();
        assertThat(hints.path("chapters")).isEmpty();

        Person grownUp = Accounts.signedIn(port, mailbox);
        grownUp.browser().patch("/api/me", Browser.json("adultConfirmed", true));
        assertThat(found(grownUp.browser(), "вежа " + word, "relevance")).containsExactly(adult.slug());
        assertThat(read(grownUp.browser().get("/api/search/hints?q=" + word)).path("chapters"))
                .extracting(chapter -> chapter.path("novelSlug").asString()).containsExactly(adult.slug());
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
