package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.AUTOTRANSLATE_PRESET;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.support.Browser.json;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 19, п. 8: a person changes the model of a quality's step — only to a model the site's
 * qualities use — and keeps it as «мій набір», to rename, delete and pick first next time.
 */
@IntegrationTest
class MySetsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person owner;
    Person reader;
    long edition;
    Long siteHigh;
    long high;

    @BeforeEach
    void aReaderWithShahsAndAQualityOfFakeModels() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        reader = Accounts.signedIn(port, mailbox);
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
        assertThat(owner.browser().post("/api/admin/users/" + reader.nick() + "/shahs", json("shah", 50)).status()).isEqualTo(201);
        // «Висока» for this test is a set of models the fake OpenRouter has.
        siteHigh = db.select(AUTOTRANSLATE_PRESET.ID).from(AUTOTRANSLATE_PRESET).where(AUTOTRANSLATE_PRESET.QUALITY.eq("high"))
                .fetchOne(AUTOTRANSLATE_PRESET.ID);
        db.update(AUTOTRANSLATE_PRESET).setNull(AUTOTRANSLATE_PRESET.QUALITY).where(AUTOTRANSLATE_PRESET.QUALITY.eq("high")).execute();
        high = db.insertInto(AUTOTRANSLATE_PRESET).set(AUTOTRANSLATE_PRESET.POSITION, 0)
                .set(AUTOTRANSLATE_PRESET.NAME, "Тестова висока " + System.nanoTime()).set(AUTOTRANSLATE_PRESET.SUMMARY, "Fake Better.")
                .set(AUTOTRANSLATE_PRESET.RATING, new java.math.BigDecimal("4.0"))
                .set(AUTOTRANSLATE_PRESET.ANALYZE_MODEL, "openai/gpt-4.1-mini").set(AUTOTRANSLATE_PRESET.TRANSLATE_MODEL, "fake/better")
                .set(AUTOTRANSLATE_PRESET.ANALYZE_MUSD_PER_KCHAR, 1_000L).set(AUTOTRANSLATE_PRESET.TRANSLATE_MUSD_PER_KCHAR, 50_000L)
                .set(AUTOTRANSLATE_PRESET.QUALITY, "high")
                .returning(AUTOTRANSLATE_PRESET.ID).fetchOne(AUTOTRANSLATE_PRESET.ID);
        edition = read(reader.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[]}""")).path("editionId").asLong();
        read(reader.browser().post(base() + "/originals/separate", JSON.writeValueAsString(Map.of(
                "language", "en", "chapters", List.of(Map.of("title", "Chapter 1", "text", "Zorian woke up."))))));
    }

    @AfterEach
    void theSitesQualityBack() {
        db.deleteFrom(AUTOTRANSLATE_PRESET).where(AUTOTRANSLATE_PRESET.ID.eq(high)).execute();
        if (siteHigh != null) {
            db.update(AUTOTRANSLATE_PRESET).set(AUTOTRANSLATE_PRESET.QUALITY, "high").where(AUTOTRANSLATE_PRESET.ID.eq(siteHigh)).execute();
        }
    }

    @Test
    void aChangedQualityIsSavedRunRenamedAndDeleted() {
        String run = base() + "/autotranslate";
        JsonNode overview = read(reader.browser().get(run));
        assertThat(overview.path("peopleModels").valueStream().map(JsonNode::asString).toList()).contains("openai/gpt-4.1-mini", "fake/better");
        assertThat(overview.path("mySets")).isEmpty();

        Map<String, Object> changed = Map.of("analyze", "openai/gpt-4.1-mini", "translate", "openai/gpt-4.1-mini");
        assertThat(reader.browser().post(run + "/quote", JSON.writeValueAsString(Map.of("to", 1, "preset", high,
                "models", Map.of("translate", "openai/gpt-4.1-mini")))).status()).as("a model the qualities use").isEqualTo(200);
        Response foreign = reader.browser().post(run + "/quote", JSON.writeValueAsString(Map.of("to", 1, "preset", high,
                "models", Map.of("translate", "fake/plain"))));
        assertThat(foreign.status()).isEqualTo(400);
        assertThat(foreign.body()).contains("сайт людям не пропонує");

        String sets = "/api/studio/autotranslate/my-sets";
        long mine = read(reader.browser().post(sets, JSON.writeValueAsString(Map.of("name", "Мій дешевий", "basedOn", high,
                "models", changed)))).path("id").asLong();
        assertThat(reader.browser().post(sets, JSON.writeValueAsString(Map.of("name", "Мій дешевий", "basedOn", high,
                "models", changed))).status()).as("one name once").isEqualTo(400);
        assertThat(reader.browser().post(sets, JSON.writeValueAsString(Map.of("name", "Чужий", "models",
                Map.of("analyze", "openai/gpt-4.1-mini", "translate", "fake/plain")))).status()).isEqualTo(400);
        JsonNode listed = read(reader.browser().get(run)).path("mySets").path(0);
        assertThat(listed.path("name").asString()).isEqualTo("Мій дешевий");
        assertThat(listed.path("basedOn").asLong()).isEqualTo(high);
        assertThat(listed.path("translate").asString()).isEqualTo("openai/gpt-4.1-mini");
        assertThat(listed.path("proofread").isNull()).isTrue();

        assertThat(reader.browser().put("/api/studio/autotranslate/choice", JSON.writeValueAsString(Map.of("preset", high,
                "steps", List.of("analyze", "translate"), "publish", true, "mySet", mine))).status()).isBetween(200, 204);
        assertThat(read(reader.browser().get(run)).path("remembered").path("mySet").asLong()).as("offered first next time").isEqualTo(mine);

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().delete(sets + "/" + mine).status()).as("only one's own").isEqualTo(404);
        assertThat(reader.browser().put(sets + "/" + mine, json("name", "Швидкий мій")).status()).isBetween(200, 204);
        assertThat(read(reader.browser().get(sets)).path(0).path("name").asString()).isEqualTo("Швидкий мій");
        assertThat(reader.browser().delete(sets + "/" + mine).status()).isBetween(200, 204);
        assertThat(read(reader.browser().get(sets))).isEmpty();
        assertThat(read(reader.browser().get(run)).path("remembered").path("mySet").isNull()).as("a deleted set is forgotten").isTrue();
    }

    private String base() {
        return "/api/studio/editions/" + edition;
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
