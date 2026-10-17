package space.panrid.novelka.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_FACT;
import static space.panrid.novelka.jooq.Tables.NOVEL;

import java.net.URLEncoder;
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

/** «Хто це» in the reader: the glossary's names in a selection, told only as far as the reader's chapter. */
@IntegrationTest
class WhoIsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    String slug;
    long novelId;
    Person admin;

    @BeforeEach
    void aNovelWithAGlossary() {
        admin = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "admin").where(ACCOUNT.NICK.eq(admin.nick())).execute();
        String title = "Маг води " + UUID.randomUUID().toString().substring(0, 6);
        Response imported = admin.browser().upload("/api/admin/novels",
                Map.of("title", title, "author", "Тадаші Хісахо", "description", "Опис.", "tags", "Фентезі", "kind", "machine", "adult", "false"),
                "file", "mag.md", "text/markdown", "# Пролог\nРьо відкрив очі.\n\n# Друга\nАбель прийшов.".getBytes(StandardCharsets.UTF_8));
        assertThat(imported.status()).as(imported.body()).isBetween(200, 299);
        slug = JSON.readTree(imported.body()).path("slug").asString();
        novelId = db.select(NOVEL.ID).from(NOVEL).where(NOVEL.SLUG.eq(slug)).fetchSingle().value1();

        long ryo = entry("Рьо", "character", "Головний герой.", 1, "approved");
        fact(ryo, 1, "Має чорне волосся.");
        fact(ryo, 5, "Стає учнем мага.");
        entry("Крижаний спис", "term", "Заклинання.", 2, "new");
        entry("Абель", "character", "Мечник.", 2, "rejected");
    }

    @Test
    void aReaderSeesWhoANameIsWithoutSpoilersAndInAnyForm() {
        Browser guest = new Browser(port);
        JsonNode early = who(guest, 2, "Рьо");
        assertThat(early.size()).isEqualTo(1);
        assertThat(early.path(0).path("kind").asString()).isEqualTo("character");
        assertThat(early.path(0).path("facts")).extracting(JsonNode::asString).containsExactly("Головний герой.", "Має чорне волосся.");
        assertThat(who(guest, 7, "Рьо").path(0).path("facts").size()).as("chapter 5 is read by now").isEqualTo(3);

        JsonNode sentence = who(guest, 3, "Рьо кинув Крижаного списа");
        assertThat(sentence).extracting(card -> card.path("ukrainian").asString()).as("the longest name first; other forms count")
                .containsExactly("Крижаний спис", "Рьо");
        assertThat(who(guest, 3, "Абель").size()).as("a rejected entry is not the novel's").isZero();
        assertThat(who(guest, 1, "Крижаний спис").path(0).path("facts").size()).as("not met by chapter 1 yet").isZero();
        assertThat(who(guest, 3, "стіл").size()).isZero();
    }

    @Test
    void theLampMarksOnlyWordsTheReadersChapterHasMetAndToldAbout() {
        Browser guest = new Browser(port);
        assertThat(terms(guest, 1)).containsExactly("Рьо");
        assertThat(terms(guest, 3)).as("the longest first; a rejected entry never").containsExactly("Крижаний спис", "Рьо");
    }

    private java.util.List<String> terms(Browser browser, int chapter) {
        Response response = browser.get("/api/novels/" + slug + "/terms?chapter=" + chapter);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        java.util.List<String> names = new java.util.ArrayList<>();
        JSON.readTree(response.body()).forEach(term -> names.add(term.path("ukrainian").asString()));
        return names;
    }

    @Test
    void withTheOriginalOnTheSiteAWordCountsOnlyWhereItsOriginalStandsAndTheTeamTiesTheRest() {
        long edition = db.select(space.panrid.novelka.jooq.Tables.EDITION.ID).from(space.panrid.novelka.jooq.Tables.EDITION)
                .where(space.panrid.novelka.jooq.Tables.EDITION.NOVEL_ID.eq(novelId)).fetchSingle().value1();
        long ryo = db.select(GLOSSARY_ENTRY.ID).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId), GLOSSARY_ENTRY.UKRAINIAN.eq("Рьо"))
                .fetchSingle().value1();
        long strike = entry("до", "term", "Удар кендо по корпусу.", 1, "approved");
        form(ryo, "涼");
        form(strike, "胴");
        // The original of chapter 2: its paragraph names Ryo, not the kendo strike.
        String block = JSON.readTree(new Browser(port).get("/api/novels/" + slug + "/chapters/2").body()).path("blocks").path(0).path("id").asString();
        db.insertInto(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.EDITION_ID, edition)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.NUMBER, 2)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.TITLE, "第2話")
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.BLOCKS, org.jooq.JSONB.valueOf(
                        "[{\"id\":\"" + block + "\",\"type\":\"paragraph\",\"content\":[{\"text\":\"アベルが涼の家に来た。\",\"marks\":[]}]}]"))
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.CHARS, 11)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.LANGUAGE, "ja").execute();

        Browser guest = new Browser(port);
        JsonNode hints = read(guest.get("/api/novels/" + slug + "/hints?chapter=2"));
        assertThat(hints.path("byOriginal").asBoolean()).isTrue();
        assertThat(hints.path("links")).extracting(link -> link.path("entryId").asLong()).containsExactly(ryo);
        assertThat(who(guest, 2, "до", block).size()).as("«до» here is a conjunction").isZero();
        assertThat(who(guest, 2, "Рьо", block).path(0).path("ukrainian").asString()).isEqualTo("Рьо");
        assertThat(who(guest, 2, "до").size()).as("without the paragraph: by the words, as before").isEqualTo(1);

        // The team ties «до» here by hand, then unties it.
        String links = "/api/studio/editions/" + edition + "/chapters/2/glossary-links";
        assertThat(guest.post(links, "{\"blockId\":\"" + block + "\",\"entryId\":" + strike + ",\"form\":\"до\",\"linked\":true}").status())
                .as("only the team").isEqualTo(401);
        read(admin.browser().post(links, "{\"blockId\":\"" + block + "\",\"entryId\":" + strike + ",\"form\":\"до\",\"linked\":true}"));
        assertThat(who(guest, 2, "до", block).path(0).path("ukrainian").asString()).isEqualTo("до");
        read(admin.browser().post(links, "{\"blockId\":\"" + block + "\",\"entryId\":" + strike + ",\"form\":\"до\",\"linked\":false}"));
        assertThat(who(guest, 2, "до", block).size()).isZero();
        assertThat(read(guest.get("/api/novels/" + slug + "/hints?chapter=2")).path("links").toString()).contains("\"linked\":false");
    }

    private void form(long entryId, String original) {
        db.insertInto(space.panrid.novelka.jooq.Tables.GLOSSARY_FORM).set(space.panrid.novelka.jooq.Tables.GLOSSARY_FORM.ENTRY_ID, entryId)
                .set(space.panrid.novelka.jooq.Tables.GLOSSARY_FORM.NOVEL_ID, novelId)
                .set(space.panrid.novelka.jooq.Tables.GLOSSARY_FORM.LANGUAGE, "ja")
                .set(space.panrid.novelka.jooq.Tables.GLOSSARY_FORM.ORIGINAL, original).execute();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }

    private JsonNode who(Browser browser, int chapter, String text) {
        return who(browser, chapter, text, null);
    }

    private JsonNode who(Browser browser, int chapter, String text, String block) {
        Response response = browser.get("/api/novels/" + slug + "/who?chapter=" + chapter + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + (block == null ? "" : "&block=" + block));
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private long entry(String ukrainian, String kind, String note, int chapter, String status) {
        return db.insertInto(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.NOVEL_ID, novelId).set(GLOSSARY_ENTRY.UKRAINIAN, ukrainian)
                .set(GLOSSARY_ENTRY.KIND, kind).set(GLOSSARY_ENTRY.NOTE, note).set(GLOSSARY_ENTRY.SOURCE_CHAPTER, chapter)
                .set(GLOSSARY_ENTRY.STATUS, status).returning(GLOSSARY_ENTRY.ID).fetchOne(GLOSSARY_ENTRY.ID);
    }

    private void fact(long entryId, int chapter, String text) {
        db.insertInto(GLOSSARY_FACT).set(GLOSSARY_FACT.ENTRY_ID, entryId).set(GLOSSARY_FACT.CHAPTER, chapter).set(GLOSSARY_FACT.FACT, text).execute();
    }
}
