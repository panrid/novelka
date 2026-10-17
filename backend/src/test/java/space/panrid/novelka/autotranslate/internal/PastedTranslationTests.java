package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_FORM;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.support.Browser.json;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 19, крок 3: the chapters a team pasted are translated as the ones fetched from a site
 * are — analysis, glossary, translation, proofreading — with instructions for their language,
 * and published or left as drafts.
 */
@IntegrationTest
class PastedTranslationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    FakeModel model;

    @Autowired
    Worker worker;

    Person owner;
    long edition;

    @BeforeEach
    void theSiteOwnerTranslatesAnEnglishNovel() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        model.reset();
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[]}""")).path("editionId").asLong();
    }

    @Test
    void chaptersPastedOneByOneAreTranslatedFromEnglishAndPublished() {
        JsonNode preview = read(owner.browser().post(originals() + "/separate/preview", separate(
                chapter("Chapter 1: Good Morning Brother", "Zorian woke up.\n“Morning!” Kirielle shouted.\nChapter 2"),
                chapter("", "He went to the academy."))));
        assertThat(preview.path("chapters").size()).isEqualTo(2);
        assertThat(preview.path("chapters").path(0).path("title").asString()).isEqualTo("Chapter 1: Good Morning Brother");
        assertThat(preview.path("chapters").path(0).path("paragraphs").asInt())
                .as("in its own field a line like a heading is still text").isEqualTo(3);
        assertThat(preview.path("chapters").path(1).path("title").asString()).isEmpty();
        assertThat(owner.browser().post(originals() + "/separate", separate(chapter("Chapter 3", "  "))).status())
                .as("a chapter needs its text").isEqualTo(400);
        read(owner.browser().post(originals() + "/separate", separate(
                chapter("Chapter 1: Good Morning Brother", "Zorian woke up.\n“Morning!” Kirielle shouted."),
                chapter("Chapter 2", "He went to the academy."))));

        JsonNode overview = read(owner.browser().get(base()));
        assertThat(overview.path("pasted").asBoolean()).isTrue();
        assertThat(overview.path("sourceChapters").asInt()).as("the last chapter pasted").isEqualTo(2);
        assertThat(owner.browser().post(base() + "/quote", json("to", 3)).body()).contains("Оригінал вставлено до глави 2");
        assertThat(read(owner.browser().post(base() + "/quote", json("to", 2))).path("chapters").asInt()).isEqualTo(2);
        assertThat(owner.browser().put("/api/studio/editions/" + edition + "/source-watch", json("watching", true)).status())
                .as("a pasted novel has no site to watch").isEqualTo(400);

        read(owner.browser().post(base() + "/jobs", json("to", 2)));
        worker.drain();

        assertThat(db.select(JOB.STATE).from(JOB).where(JOB.EDITION_ID.eq(edition)).fetchSingle().value1()).isEqualTo("done");
        String translate = system("translation");
        assertThat(translate).contains("from English into Ukrainian").doesNotContain("「");
        assertThat(system("glossary")).contains("English web novel");
        assertThat(system("proofread")).contains("English original");

        String slug = read(owner.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        JsonNode first = read(new Browser(port).get("/api/novels/" + slug + "/chapters/1"));
        assertThat(first.path("title").asString()).as("the number is the site's: «Глава 9.» is cut off").isEqualTo("Світло");
        assertThat(first.path("blocks").path(0).path("content").path(0).path("text").asString()).isEqualTo("переклад b1 ✓");
        assertThat(db.select(EDITION.KIND).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1())
                .as("readers learn the translation has machine chapters").isEqualTo("mixed");

        long novel = db.select(EDITION.NOVEL_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1();
        assertThat(db.select(NOVEL.SOURCE_LANGUAGE).from(NOVEL).where(NOVEL.ID.eq(novel)).fetchSingle().value1()).isEqualTo("en");
        assertThat(db.select(GLOSSARY_FORM.LANGUAGE).from(GLOSSARY_FORM).join(GLOSSARY_ENTRY).on(GLOSSARY_ENTRY.ID.eq(GLOSSARY_FORM.ENTRY_ID))
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(novel), GLOSSARY_ENTRY.UKRAINIAN.eq("Зоріан")).fetch(GLOSSARY_FORM.LANGUAGE))
                .as("the glossary keeps the English form").containsExactly("en");
    }

    @Test
    void aRunMayLeaveItsChaptersAsDraftsAndSkipsChaptersWithNoOriginal() {
        read(owner.browser().post(originals() + "/separate", separate(chapter("Chapter 1", "Zorian woke up."))));
        read(owner.browser().post(originals() + "/separate", JSON.writeValueAsString(Map.of(
                "chapters", List.of(chapter("Chapter 3", "Morning came.")), "first", 3, "language", "en"))));
        // Chapter 3 of the editor already holds my own unfinished draft: it stays mine.
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        owner.browser().put("/api/studio/editions/" + edition + "/chapters/3/draft", """
                {"title":"Моя","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Мій абзац","marks":[]}]}],"baseRevisionId":null}""");

        JsonNode quote = read(owner.browser().post(base() + "/quote", json("to", 3, "publish", false)));
        assertThat(quote.path("chapters").asInt()).as("chapter 2 has no original").isEqualTo(2);
        read(owner.browser().post(base() + "/jobs", json("to", 3, "publish", false)));
        worker.drain();

        JsonNode one = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/1"));
        assertThat(one.path("published").asBoolean()).isFalse();
        assertThat(one.path("draft").path("title").asString()).isEqualTo("Світло");
        assertThat(one.path("draft").path("blocks").path(0).path("content").path(0).path("text").asString()).isEqualTo("переклад b1 ✓");
        JsonNode three = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/3"));
        assertThat(three.path("draft").path("title").asString()).isEqualTo("Моя");
        assertThat(db.select(EDITION.KIND).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1())
                .as("nothing published").isEqualTo("human");
        long job = db.select(JOB.ID).from(JOB).where(JOB.EDITION_ID.eq(edition)).fetchSingle().value1();
        String log = read(owner.browser().get(base() + "/jobs/" + job + "/log")).path("events").toString();
        assertThat(log).contains("drafted").contains("draft_kept");
    }

    @Test
    void anOriginalRemovedDuringTheRunStopsItWithAnExplanation() {
        read(owner.browser().post(originals() + "/separate", separate(chapter("Chapter 1", "Zorian woke up."),
                chapter("Chapter 2", "He went to the academy."))));
        read(owner.browser().post(base() + "/jobs", json("to", 2)));
        read(owner.browser().delete(originals() + "/2"));
        worker.drain();
        String error = db.select(JOB.ERROR).from(JOB).where(JOB.EDITION_ID.eq(edition)).fetchSingle().value1();
        assertThat(error).contains("Оригіналу глави 2 немає");
    }

    @Test
    void aTitleTheModelLeavesInJapaneseIsAskedForAgainAndThenLeftOut() {
        read(owner.browser().post(originals() + "/separate", JSON.writeValueAsString(Map.of(
                "chapters", List.of(chapter("参考資料３（図面）", "図面の説明。")), "language", "ja"))));
        read(owner.browser().post(base() + "/jobs", json("to", 1)));
        worker.drain();
        assertThat(model.calls.stream().filter("glossary"::equals).count()).as("asked three times").isEqualTo(3);
        String slug = read(owner.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        assertThat(read(new Browser(port).get("/api/novels/" + slug + "/chapters/1")).path("title").asString()).isEmpty();
        long job = db.select(JOB.ID).from(JOB).where(JOB.EDITION_ID.eq(edition)).fetchSingle().value1();
        assertThat(read(owner.browser().get(base() + "/jobs/" + job + "/log")).path("events").toString()).contains("title_untranslated");
    }

    private String system(String schema) {
        return model.requests.stream()
                .filter(request -> request.path("response_format").path("json_schema").path("name").asString("").equals(schema))
                .findFirst().orElseThrow().path("messages").path(0).path("content").asString();
    }

    private static Map<String, String> chapter(String title, String text) {
        return Map.of("title", title, "text", text);
    }

    @SafeVarargs
    private static String separate(Map<String, String>... chapters) {
        return JSON.writeValueAsString(Map.of("chapters", List.of(chapters), "language", "en"));
    }

    private String base() {
        return "/api/studio/editions/" + edition + "/autotranslate";
    }

    private String originals() {
        return "/api/studio/editions/" + edition + "/originals";
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
