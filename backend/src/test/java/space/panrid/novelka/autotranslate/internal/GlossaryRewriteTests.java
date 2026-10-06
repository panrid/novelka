package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.support.Browser.json;

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

/** A glossary word changed in every chapter that uses it (етап 17). */
@IntegrationTest
class GlossaryRewriteTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Test
    void aChangedNameIsFoundInEveryChapterAndChangedBySuggestionsOrAtOnce() {
        Person translator = Accounts.signedIn(port, mailbox);
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Словник у главах %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        String[] texts = {"Орест прокинувся. Ореста кликали.", "Нікого не було.", "Я віддав Орестові ліхтар."};
        for (int number = 1; number <= 3; number++) {
            translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
            read(translator.browser().post("/api/studio/editions/" + edition + "/chapters/" + number + "/publish", """
                    {"title":"Глава %d","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"%s","marks":[]}]}]}"""
                    .formatted(number, texts[number - 1])));
        }
        long novel = db.select(EDITION.NOVEL_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle(EDITION.NOVEL_ID);
        long entry = db.insertInto(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.NOVEL_ID, novel).set(GLOSSARY_ENTRY.UKRAINIAN, "Орест")
                .set(GLOSSARY_ENTRY.KIND, "character").returning(GLOSSARY_ENTRY.ID).fetchOne(GLOSSARY_ENTRY.ID);
        String base = "/api/studio/editions/" + edition + "/glossary/" + entry;

        JsonNode found = read(translator.browser().get(base + "/occurrences"));
        assertThat(found.path("total").asInt()).isEqualTo(3);
        assertThat(found.path("chapters").findValuesAsString("label")).containsExactly("1", "3");

        read(translator.browser().put(base, json("ukrainian", "Остап", "kind", "character", "gender", "male", "note", "")));
        JsonNode proposed = read(translator.browser().post(base + "/rewrite", json("from", "Орест", "apply", false)));
        assertThat(proposed.path("paragraphs").asInt()).isEqualTo(2);
        assertThat(proposed.path("chapters").asInt()).isEqualTo(2);
        JsonNode queue = read(translator.browser().get("/api/studio/editions/" + edition + "/chapters/1/suggestions"));
        assertThat(queue.path(0).path("proposed").toString()).contains("Остап прокинувся. Остапа кликали.");
        Browser reader = new Browser(port);
        String slug = read(translator.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        assertThat(read(reader.get("/api/novels/" + slug + "/chapters/1")).toString()).as("only proposed so far").contains("Орест прокинувся");

        // At once: the same change, accepted straight away, so it stays in the chapter's history.
        read(translator.browser().put(base, json("ukrainian", "Устим", "kind", "character", "gender", "male", "note", "")));
        read(translator.browser().post(base + "/rewrite", json("from", "Орест", "apply", true)));
        assertThat(read(reader.get("/api/novels/" + slug + "/chapters/3")).toString()).contains("Я віддав Устимові ліхтар.");
        assertThat(read(translator.browser().get("/api/studio/editions/" + edition + "/chapters/3/revisions")).size()).isEqualTo(2);
    }

    @Test
    void theModelPutsTheNewNameIntoTheRightCaseAndNothingElse() {
        Person translator = Accounts.signedIn(port, mailbox);
        db.update(space.panrid.novelka.jooq.Tables.ACCOUNT).set(space.panrid.novelka.jooq.Tables.ACCOUNT.SITE_ROLE, "reader")
                .where(space.panrid.novelka.jooq.Tables.ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(space.panrid.novelka.jooq.Tables.ACCOUNT).set(space.panrid.novelka.jooq.Tables.ACCOUNT.SITE_ROLE, "owner")
                .where(space.panrid.novelka.jooq.Tables.ACCOUNT.NICK.eq(translator.nick())).execute();
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Узгодження %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        read(translator.browser().post("/api/studio/editions/" + edition + "/chapters/1/publish", """
                {"title":"Глава 1","blocks":[
                  {"id":"b1","type":"paragraph","content":[{"text":"Я говорив з Марією.","marks":[]}]},
                  {"id":"b2","type":"paragraph","content":[{"text":"ЗАЙВЕ: Марія пішла.","marks":[]}]}]}"""));
        long novel = db.select(EDITION.NOVEL_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle(EDITION.NOVEL_ID);
        long entry = db.insertInto(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.NOVEL_ID, novel).set(GLOSSARY_ENTRY.UKRAINIAN, "Мірела")
                .set(GLOSSARY_ENTRY.KIND, "character").returning(GLOSSARY_ENTRY.ID).fetchOne(GLOSSARY_ENTRY.ID);

        read(translator.browser().post("/api/studio/editions/" + edition + "/glossary/" + entry + "/rewrite",
                json("from", "Марія", "apply", true, "ai", true)));
        String slug = read(translator.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        String text = read(new Browser(port).get("/api/novels/" + slug + "/chapters/1")).toString();
        assertThat(text).as("the case put right").contains("Я говорив з Мірелою.");
        assertThat(text).as("an answer that rewrote more is dropped: the plain replacement stays").contains("ЗАЙВЕ: Мірела пішла.");
    }

    @Test
    void aNewGenderMakesTheWordsAboutTheCharacterAgreeAsSuggestions() {
        Person translator = Accounts.signedIn(port, mailbox);
        db.update(space.panrid.novelka.jooq.Tables.ACCOUNT).set(space.panrid.novelka.jooq.Tables.ACCOUNT.SITE_ROLE, "reader")
                .where(space.panrid.novelka.jooq.Tables.ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(space.panrid.novelka.jooq.Tables.ACCOUNT).set(space.panrid.novelka.jooq.Tables.ACCOUNT.SITE_ROLE, "owner")
                .where(space.panrid.novelka.jooq.Tables.ACCOUNT.NICK.eq(translator.nick())).execute();
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Рід %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        read(translator.browser().post("/api/studio/editions/" + edition + "/chapters/1/publish", """
                {"title":"Глава 1","blocks":[
                  {"id":"b1","type":"paragraph","content":[{"text":"Сашко пішов додому, сміливий як завжди.","marks":[]}]},
                  {"id":"b2","type":"paragraph","content":[{"text":"Падав сніг.","marks":[]}]}]}"""));
        long novel = db.select(EDITION.NOVEL_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle(EDITION.NOVEL_ID);
        long entry = db.insertInto(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.NOVEL_ID, novel).set(GLOSSARY_ENTRY.UKRAINIAN, "Сашко")
                .set(GLOSSARY_ENTRY.KIND, "character").set(GLOSSARY_ENTRY.GENDER, "female").returning(GLOSSARY_ENTRY.ID)
                .fetchOne(GLOSSARY_ENTRY.ID);

        JsonNode done = read(translator.browser().post("/api/studio/editions/" + edition + "/glossary/" + entry + "/regender",
                json("apply", false)));
        assertThat(done.path("paragraphs").asInt()).as("only the paragraph that names her").isEqualTo(1);
        JsonNode queue = read(translator.browser().get("/api/studio/editions/" + edition + "/chapters/1/suggestions"));
        assertThat(queue.path(0).path("proposed").toString()).contains("Сашко пішла додому, смілива як завжди.");
    }

    @Test
    void anAnswerMayTouchOnlyTheWordsAroundTheChange() {
        assertThat(Agreement.small("Я говорив з Марією.", "Я говорив з Мірелєю.", "Я говорив з Мірелою.")).isTrue();
        assertThat(Agreement.small("Я говорив з Марією.", "Я говорив з Мірелєю.", "Ми мовчали біля моря довго.")).isFalse();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
