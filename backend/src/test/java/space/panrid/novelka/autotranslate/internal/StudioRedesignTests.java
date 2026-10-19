package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 20, крок 4: «Хід перекладу» наживо shows what a run really saved, chapter by chapter;
 * «Мої переклади» know how far each translation has got; the glossary approves its new entries
 * at once and gives an entry a portrait for «Хто це» from «Мої картинки».
 */
@IntegrationTest
class StudioRedesignTests {

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
    Person translator;

    @BeforeEach
    void theSiteOwnerAndATranslator() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        translator = Accounts.signedIn(port, mailbox);
        model.reset();
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
    }

    @Test
    void theLiveRunShowsTheOriginalBesideTheTranslationItSaved() {
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Доглядач маяка","description":[],"tags":[],"titleOriginal":"The Lighthouse Keeper","language":"en"}"""))
                .path("editionId").asLong();
        assertThat(db.select(NOVEL.TITLE_ORIGINAL, NOVEL.SOURCE_LANGUAGE).from(NOVEL)
                .where(NOVEL.ID.eq(db.select(space.panrid.novelka.jooq.Tables.EDITION.NOVEL_ID).from(space.panrid.novelka.jooq.Tables.EDITION)
                        .where(space.panrid.novelka.jooq.Tables.EDITION.ID.eq(edition))))
                .fetchSingle().intoArray())
                .as("«Новий переклад» keeps the name in the original and its language").containsExactly("The Lighthouse Keeper", "en");
        String base = "/api/studio/editions/" + edition;
        read(translator.browser().post(base + "/originals/separate", JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1",
                        "text", "The log lay where the keeper left it " + System.nanoTime() + ".\n\nLina hesitated."))))));
        assertThat(owner.browser().post("/api/admin/users/" + translator.nick() + "/shahs", json("shah", 10)).status()).isEqualTo(201);

        JsonNode mine = read(translator.browser().get("/api/studio")).path(0);
        assertThat(mine.path("originals").asInt()).as("one chapter of the original pasted").isEqualTo(1);
        assertThat(mine.path("sourceLanguage").asString()).isEqualTo("en");

        long job = read(translator.browser().post(base + "/autotranslate/jobs", json("to", 1))).path("id").asLong();
        String live = base + "/autotranslate/jobs/" + job + "/live";
        worker.drain();
        JsonNode after = read(translator.browser().get(live));
        assertThat(after.path("job").path("state").asString()).isEqualTo("done");
        JsonNode chapter = after.path("chapter");
        assertThat(chapter.path("number").asInt()).isEqualTo(1);
        assertThat(chapter.path("stage").asString()).isEqualTo("done");
        assertThat(chapter.path("language").asString()).isEqualTo("en");
        assertThat(chapter.path("title").asString()).isEqualTo("Chapter 1");
        assertThat(after.path("steps").path(0).path("number").asInt()).isEqualTo(1);
        assertThat(chapter.path("paragraphs")).isNotEmpty();
        assertThat(chapter.path("paragraphs").path(0).path("original").asString()).startsWith("The log lay");
        assertThat(chapter.path("paragraphs")).allSatisfy(paragraph -> assertThat(paragraph.path("translation").asString()).isNotBlank());
        assertThat(read(translator.browser().get(live + "?chapter=1")).path("chapter").path("number").asInt()).isEqualTo(1);

        assertThat(read(translator.browser().get("/api/studio")).path(0).path("lastChapter").asInt())
                .as("«Мої переклади» open the newest chapter").isEqualTo(1);
        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().get(live).status()).as("only the team watches its runs").isEqualTo(403);
    }

    @Test
    void theGlossaryApprovesNewEntriesAndTakesAPortraitFromMyPictures() {
        long edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[],"rightsConfirmed":true,"language":"en"}""")).path("editionId").asLong();
        String base = "/api/studio/editions/" + edition;
        read(owner.browser().post(base + "/originals/separate", JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1", "text", "Zorian woke up in Cirin."))))));
        read(owner.browser().post(base + "/glossary", JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", "Зоріан",
                "original", "Zorian", "kind", "character", "gender", "male"))));
        long entry = read(owner.browser().get(base + "/glossary?q=Zorian")).path("items").path(0).path("id").asLong();
        assertThat(entry).as("the search finds an entry by its original too").isPositive();
        assertThat(read(owner.browser().get(base + "/glossary?kind=place")).path("total").asInt()).isZero();
        assertThat(read(owner.browser().get(base + "/glossary?kind=character")).path("items").path(0).path("original").asString())
                .isEqualTo("Zorian");

        read(owner.browser().post(base + "/glossary/status", JSON.writeValueAsString(Map.of("ids", List.of(entry), "status", "new"))));
        assertThat(read(owner.browser().post(base + "/glossary/approve-new", "{}")).path("changed").asInt()).isEqualTo(1);
        assertThat(read(owner.browser().get(base + "/glossary")).path("counts").path("new").asInt()).isZero();

        long picture = read(owner.browser().upload("/api/media/images", "file", "zorian.png", "image/png", Pictures.png(600, 800, Color.BLUE)))
                .path("id").asLong();
        Person stranger = Accounts.signedIn(port, mailbox);
        long theirs = read(stranger.browser().upload("/api/media/images", "file", "other.png", "image/png", Pictures.png(600, 800, Color.RED)))
                .path("id").asLong();
        Response foreign = owner.browser().put(base + "/glossary/" + entry + "/portrait", json("imageId", theirs));
        assertThat(foreign.status()).as("only from one's own «Мої картинки»").isEqualTo(400);
        assertThat(stranger.browser().put(base + "/glossary/" + entry + "/portrait", json("imageId", theirs)).status()).isEqualTo(403);

        assertThat(owner.browser().put(base + "/glossary/" + entry + "/portrait", json("imageId", picture)).status()).isBetween(200, 204);
        assertThat(read(owner.browser().get(base + "/glossary")).path("items").path(0).path("portraitUrl").asString()).isNotBlank();
        assertThat(owner.browser().put(base + "/glossary/" + entry + "/portrait", "{\"imageId\":null}").status()).isBetween(200, 204);
        assertThat(read(owner.browser().get(base + "/glossary")).path("items").path(0).path("portraitUrl").isNull()).isTrue();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
