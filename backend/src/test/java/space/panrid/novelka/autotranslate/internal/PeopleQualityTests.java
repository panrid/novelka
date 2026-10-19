package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.AUTOTRANSLATE_PRESET;
import static space.panrid.novelka.jooq.Tables.JOB;
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
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 19, п. 3: people pick a quality — «Найкраща», «Висока», «Швидка чернетка» — with its price in
 * шаги, not models; the site owner says which set of models each quality is; «Запам'ятати для
 * наступних» keeps the person's choice for their next runs.
 */
@IntegrationTest
class PeopleQualityTests {

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

    @BeforeEach
    void aReaderWithShahsAndAPastedChapter() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        reader = Accounts.signedIn(port, mailbox);
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
        assertThat(owner.browser().post("/api/admin/users/" + reader.nick() + "/shahs", json("shah", 50)).status()).isEqualTo(201);
        edition = read(reader.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[]}""")).path("editionId").asLong();
        read(reader.browser().post("/api/studio/editions/" + edition + "/originals/separate", JSON.writeValueAsString(Map.of(
                "language", "en", "chapters", List.of(Map.of("title", "Chapter 1", "text", "Zorian woke up."))))));
    }

    @Test
    void peoplePickAQualityAndRememberIt() {
        String base = "/api/studio/editions/" + edition + "/autotranslate";
        JsonNode presets = read(reader.browser().get(base)).path("presets");
        assertThat(presets.valueStream().map(preset -> preset.path("quality").asString()).toList()).containsExactly("best", "high", "draft");
        long high = presets.path(1).path("id").asLong();
        long ownersOnly = db.select(AUTOTRANSLATE_PRESET.ID).from(AUTOTRANSLATE_PRESET).where(AUTOTRANSLATE_PRESET.QUALITY.isNull())
                .limit(1).fetchSingle().value1();

        assertThat(read(reader.browser().post(base + "/quote", json("to", 1, "preset", high))).path("reserveShah").asInt()).isPositive();
        Response refused = reader.browser().post(base + "/quote", json("to", 1, "preset", ownersOnly));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("сайт людям не пропонує");

        assertThat(read(reader.browser().get(base)).path("remembered").isNull()).isTrue();
        assertThat(reader.browser().put("/api/studio/autotranslate/choice", JSON.writeValueAsString(Map.of(
                "preset", ownersOnly, "steps", List.of("translate"), "publish", false))).status()).isEqualTo(400);
        assertThat(reader.browser().put("/api/studio/autotranslate/choice", JSON.writeValueAsString(Map.of(
                "preset", high, "steps", List.of("proofread", "translate"), "publish", false))).status()).isBetween(200, 204);
        JsonNode remembered = read(reader.browser().get(base)).path("remembered");
        assertThat(remembered.path("preset").asLong()).isEqualTo(high);
        assertThat(remembered.path("steps").valueStream().map(JsonNode::asString).toList()).containsExactly("translate", "proofread");
        assertThat(remembered.path("publish").asBoolean()).isFalse();
    }

    @Test
    void theSiteOwnerSaysWhichSetEachQualityIs() {
        String qualities = "/api/studio/autotranslate/qualities";
        assertThat(reader.browser().get(qualities).status()).isEqualTo(403);
        JsonNode sets = read(owner.browser().get(qualities));
        long first = sets.path(0).path("id").asLong();
        long second = sets.path(1).path("id").asLong();
        Map<String, Object> before = new java.util.HashMap<>();
        sets.valueStream().filter(set -> !set.path("quality").isNull())
                .forEach(set -> before.put(set.path("quality").asString(), set.path("id").asLong()));
        try {
            assertThat(owner.browser().put(qualities, JSON.writeValueAsString(Map.of("best", first, "high", first))).status())
                    .as("one set is one quality").isEqualTo(400);
            Map<String, Object> changed = new java.util.HashMap<>(Map.of("best", second, "high", first));
            changed.put("draft", null);
            JsonNode saved = read(owner.browser().put(qualities, JSON.writeValueAsString(changed)));
            assertThat(saved.path(0).path("quality").asString()).isEqualTo("high");
            assertThat(saved.path(1).path("quality").asString()).isEqualTo("best");
            assertThat(read(reader.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("presets")
                    .valueStream().map(preset -> preset.path("quality").asString()).toList()).containsExactly("best", "high");
            assertThat(read(owner.browser().get("/api/admin/audit")).path("items").path(0).path("action").asString())
                    .isEqualTo("autotranslate_qualities");
        } finally {
            read(owner.browser().put(qualities, JSON.writeValueAsString(before)));
        }
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
