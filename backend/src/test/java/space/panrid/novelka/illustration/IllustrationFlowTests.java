package space.panrid.novelka.illustration;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.AI_CALL;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.support.Browser.json;

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
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class IllustrationFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    FakeModel model;

    Person owner;
    long edition;
    String base;

    @BeforeEach
    void theOwnerTranslatesANovel() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Ліхтарник %s"}""".formatted(owner.nick()))).path("editionId").asLong();
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        base = "/api/studio/editions/" + edition;
        model.reset();
    }

    @Test
    void theOwnerSeesThePriceDescribesTheSceneDrawsItAndPutsItInTheChapter() {
        JsonNode price = read(owner.browser().get(base + "/illustrations/price"));
        assertThat(price.path("usd").decimalValue()).isEqualByComparingTo("0.040");
        assertThat(price.path("shah").asInt()).as("rounded up: a picture never looks cheaper").isEqualTo(2);

        String fragment = "Рьо стояв біля маяка, і ліхтар кидав на воду жовте світло.";
        String prompt = read(owner.browser().post(base + "/illustrations/prompt", json("fragment", fragment))).path("prompt").asString();
        assertThat(prompt).startsWith("A young man by a lighthouse").contains("light novel style");

        JsonNode drawn = read(owner.browser().post(base + "/illustrations", json("prompt", prompt, "fragment", fragment,
                "aspect", "3:4", "chapter", 1)));
        long imageId = drawn.path("imageId").asLong();
        assertThat(drawn.path("url").asString()).startsWith("/media/");
        assertThat(drawn.path("costUsd").decimalValue()).isEqualByComparingTo("0.039");
        var image = db.selectFrom(IMAGE).where(IMAGE.ID.eq(imageId)).fetchSingle();
        assertThat(image.getKind()).isEqualTo("illustration");
        assertThat(image.getPrompt()).isEqualTo(prompt);
        assertThat(image.getFragment()).isEqualTo(fragment);
        assertThat(db.select(AI_CALL.RESPONSE).from(AI_CALL).where(AI_CALL.ID.eq(image.getAiCallId())).fetchSingle().value1().data())
                .as("the journal keeps no megabytes of base64").contains("байтів").doesNotContain("base64,");

        assertThat(owner.browser().post(base + "/chapters/1/publish", """
                {"title":"Маяк","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"%s","marks":[]}]},
                {"id":"b2","type":"image","content":[],"imageId":%d}]}""".formatted(fragment, imageId)).status()).isEqualTo(200);

        JsonNode spent = read(owner.browser().get("/api/studio/illustrations/settings")).path("spent");
        assertThat(spent.path("pictures").asInt()).isGreaterThanOrEqualTo(1);

        assertThat(owner.browser().post(base + "/illustrations", json("prompt", prompt, "aspect", "2:1")).status()).isEqualTo(400);
    }

    @Test
    void theArtistIsChosenFromModelsThatDraw() {
        JsonNode artists = read(owner.browser().get("/api/studio/autotranslate/models?output=image"));
        assertThat(artists).extracting(m -> m.path("id").asString()).containsExactlyInAnyOrder("fake/painter", "fake/drawer");
        JsonNode drawer = java.util.stream.StreamSupport.stream(artists.spliterator(), false)
                .filter(m -> m.path("id").asString().equals("fake/drawer")).findFirst().orElseThrow();
        assertThat(drawer.path("chapterUsd").doubleValue()).as("about a picture's price").isBetween(0.03, 0.05);
        assertThat(read(owner.browser().get("/api/studio/autotranslate/models?q=drawer")))
                .as("a model that draws is not offered for translation").isEmpty();

        String settings = """
                {"model":"%s","microUsdPerImage":40000,"style":"soft","promptModel":"%s","promptInputPerMillion":0.4,"promptOutputPerMillion":1.6}""";
        assertThat(owner.browser().put("/api/studio/illustrations/settings", settings.formatted("openai/gpt-4.1-mini", "openai/gpt-4.1-mini")).status())
                .as("a text model cannot draw").isEqualTo(400);
        assertThat(owner.browser().put("/api/studio/illustrations/settings", settings.formatted("fake/drawer", "fake/plain")).status())
                .as("the description needs structured answers").isEqualTo(400);
        try {
            assertThat(owner.browser().put("/api/studio/illustrations/settings", settings.formatted("fake/drawer", "openai/gpt-4.1-mini")).status())
                    .isBetween(200, 204);
        } finally {
            db.execute("DELETE FROM site_setting WHERE key = 'illustration.settings'");
        }
    }

    @Test
    void withoutShahsOnlyTheSiteOwnerDraws() {
        Person translator = Accounts.signedIn(port, mailbox);
        long own = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Своє %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        assertThat(translator.browser().get("/api/studio/editions/" + own + "/illustrations/price").status()).isEqualTo(400);
        assertThat(translator.browser().post("/api/studio/editions/" + own + "/illustrations", json("prompt", "x", "aspect", "1:1")).status())
                .isEqualTo(400);
        assertThat(owner.browser().get("/api/studio/editions/" + own + "/illustrations/price").status())
                .as("the owner draws only where they translate").isEqualTo(403);
        assertThat(model.calls).isEmpty();
    }

    @Test
    void aTranslatorWithShahsDrawsAndPaysWhatThePictureCostRoundedUp() {
        Person translator = Accounts.signedIn(port, mailbox);
        long own = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Своє %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        assertThat(owner.browser().post("/api/admin/users/" + translator.nick() + "/shahs", json("shah", 3)).status()).isEqualTo(201);

        JsonNode price = read(translator.browser().get("/api/studio/editions/" + own + "/illustrations/price"));
        assertThat(price.path("shah").asInt()).as("4 cents at 7 cents a шаг").isEqualTo(1);
        assertThat(price.path("showShah").asBoolean()).isTrue();
        JsonNode drawn = read(translator.browser().post("/api/studio/editions/" + own + "/illustrations",
                json("prompt", "A lighthouse at night", "aspect", "1:1")));
        assertThat(drawn.path("costShah").asInt()).as("3,9 cents is one шаг").isEqualTo(1);
        JsonNode mine = read(translator.browser().get("/api/me/shahs"));
        assertThat(mine.path("available").asInt()).isEqualTo(2);
        assertThat(mine.path("reserved").asInt()).isZero();
        assertThat(mine.path("history").path(0).path("what").asString()).isEqualTo("Ілюстрація");
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
