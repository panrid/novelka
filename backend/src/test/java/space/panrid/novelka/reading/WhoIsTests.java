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
    void theNovelPageListsTheCharactersMetSoFarAndAllOfThemOnlyWhenAsked() {
        Browser guest = new Browser(port);
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug).body()).path("characters").asInt()).as("Абель is rejected").isEqualTo(1);
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug + "/characters").body()).size()).as("not started yet").isZero();
        JsonNode met = JSON.readTree(guest.get("/api/novels/" + slug + "/characters?chapter=3").body());
        assertThat(met).extracting(card -> card.path("ukrainian").asString()).containsExactly("Рьо");
        assertThat(met.path(0).path("facts")).extracting(JsonNode::asString).containsExactly("Головний герой.", "Має чорне волосся.");
        JsonNode all = JSON.readTree(guest.get("/api/novels/" + slug + "/characters?all=true").body());
        assertThat(all.path(0).path("facts").size()).as("spoilers, as asked").isEqualTo(3);
    }

    @Test
    void theCharactersTabCountsOnlyCharactersTheGlossaryDescribes() {
        Browser guest = new Browser(port);
        entry("Юко", "character", null, 1, "approved");
        entry("Сора", "character", "  ", 1, "approved");
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug).body()).path("characters").asInt())
                .as("names without a note or a fact are no reason for the tab").isEqualTo(1);

        long sora = db.select(GLOSSARY_ENTRY.ID).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId), GLOSSARY_ENTRY.UKRAINIAN.eq("Сора"))
                .fetchSingle().value1();
        fact(sora, 2, "Сестра Юко.");
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug).body()).path("characters").asInt()).as("a fact is a description").isEqualTo(2);

        db.deleteFrom(GLOSSARY_FACT).where(GLOSSARY_FACT.ENTRY_ID.in(db.select(GLOSSARY_ENTRY.ID).from(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId)))).execute();
        db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.NOTE, (String) null).where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId)).execute();
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug).body()).path("characters").asInt())
                .as("translated by hand, no descriptions — no tab").isZero();
    }

    @Test
    void otherNamesLeadToOneCardAndOnlyActingCharactersAreListed() {
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(admin.nick())).execute();
        long edition = db.select(space.panrid.novelka.jooq.Tables.EDITION.ID).from(space.panrid.novelka.jooq.Tables.EDITION)
                .where(space.panrid.novelka.jooq.Tables.EDITION.NOVEL_ID.eq(novelId)).fetchSingle().value1();
        long ryo = db.select(GLOSSARY_ENTRY.ID).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId), GLOSSARY_ENTRY.UKRAINIAN.eq("Рьо"))
                .fetchSingle().value1();
        long fullName = entry("Міхара Рьо", "character", "Повне ім'я героя.", 4, "approved");
        long wife = entry("дружина Лота", "character", "Дружина торговця Лота.", 2, "approved");
        long lily = entry("Лілія", "character", "Має руде волосся.", 6, "approved");
        long goblin = entry("Ґоблін", "character", "Зелений монстр.", 1, "approved");
        String base = "/api/studio/editions/" + edition + "/glossary/";
        read(admin.browser().post(base + fullName + "/same-as", "{\"mainId\":" + ryo + "}"));
        read(admin.browser().post(base + wife + "/same-as", "{\"mainId\":" + lily + "}"));

        // The model sorts those not sorted yet: the goblin is a creature, not a character of the story.
        JsonNode quote = read(admin.browser().get(base + "roles"));
        assertThat(quote.path("characters").asInt()).as("main entries only").isEqualTo(3);
        JsonNode done = read(admin.browser().post(base + "roles", "{}"));
        assertThat(done.path("acting").asInt()).isEqualTo(2);
        assertThat(read(admin.browser().get(base + "roles")).path("characters").asInt()).as("all sorted").isZero();

        Browser guest = new Browser(port);
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug + "/characters?all=true&sort=appearance").body()))
                .extracting(card -> card.path("ukrainian").asString()).as("one name per character, no creatures").containsExactly("Рьо", "Лілія");
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug + "/characters?chapter=3&sort=appearance").body()))
                .extracting(card -> card.path("ukrainian").asString()).as("her name is not told yet").containsExactly("Рьо", "дружина Лота");
        assertThat(who(guest, 7, "Міхара Рьо").path(0).path("ukrainian").asString()).as("another name, the main card").isEqualTo("Рьо");
        assertThat(who(guest, 7, "Міхара Рьо").path(0).path("facts").toString()).contains("Повне ім'я героя.").contains("Головний герой.");
        assertThat(who(guest, 7, "Ґоблін").path(0).path("ukrainian").asString()).as("still a hint").isEqualTo("Ґоблін");

        // Her name is the main one now; then the full name goes its own way again.
        read(admin.browser().post(base + wife + "/main", "{}"));
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug + "/characters?all=true&sort=appearance").body()))
                .extracting(card -> card.path("ukrainian").asString()).containsExactly("Рьо", "дружина Лота");
        read(admin.browser().delete(base + fullName + "/same-as"));
        read(admin.browser().put(base + fullName + "/in-story", "{\"inStory\":false}"));
        assertThat(JSON.readTree(guest.get("/api/novels/" + slug).body()).path("characters").asInt()).isEqualTo(2);
    }

    @Test
    void charactersAreSortedByMentionsUpToTheReadersChapterOrByName() {
        long edition = db.select(space.panrid.novelka.jooq.Tables.EDITION.ID).from(space.panrid.novelka.jooq.Tables.EDITION)
                .where(space.panrid.novelka.jooq.Tables.EDITION.NOVEL_ID.eq(novelId)).fetchSingle().value1();
        long ryo = db.select(GLOSSARY_ENTRY.ID).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId), GLOSSARY_ENTRY.UKRAINIAN.eq("Рьо"))
                .fetchSingle().value1();
        long yuko = entry("Юко", "character", "Подруга Рьо.", 1, "approved");
        long full = entry("Юко-сан", "character", "Так її кличуть.", 2, "approved");
        db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.SAME_AS, yuko).where(GLOSSARY_ENTRY.ID.eq(full)).execute();
        form(ryo, "涼");
        form(yuko, "ユコ");
        form(full, "ユコさん");
        // Chapter 1 is counted in the translation (Рьо once), chapter 2 in its original:
        // «ユコさん» is one mention, not also «ユコ»; both names count for Юко.
        db.insertInto(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.EDITION_ID, edition)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.NUMBER, 2)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.TITLE, "第2話")
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.BLOCKS, org.jooq.JSONB.valueOf(
                        "[{\"id\":\"a\",\"type\":\"paragraph\",\"content\":[{\"text\":\"ユコさんとユコ、ユコ。涼。\",\"marks\":[]}]}]"))
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.CHARS, 12)
                .set(space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL.LANGUAGE, "ja").execute();

        Browser guest = new Browser(port);
        JsonNode all = read(guest.get("/api/novels/" + slug + "/characters?all=true"));
        assertThat(all).extracting(card -> card.path("ukrainian").asString()).as("the most named first").containsExactly("Юко", "Рьо");
        assertThat(all).extracting(card -> card.path("mentions").asInt()).containsExactly(3, 2);
        assertThat(read(guest.get("/api/novels/" + slug + "/characters?chapter=1")))
                .extracting(card -> card.path("mentions").asInt()).as("only what the reader has read").containsExactly(1, 0);
        assertThat(read(guest.get("/api/novels/" + slug + "/characters?all=true&sort=name")))
                .extracting(card -> card.path("ukrainian").asString()).containsExactly("Рьо", "Юко");

        // A name added later is counted again.
        long abel = entry("Абель-сан", "character", "Мечник.", 2, "approved");
        form(abel, "ユコさんとユコ");
        assertThat(read(guest.get("/api/novels/" + slug + "/characters?all=true")))
                .extracting(card -> card.path("ukrainian").asString() + card.path("mentions").asInt()).as("equals in the order met").containsExactly("Рьо2", "Юко1", "Абель-сан1");
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
