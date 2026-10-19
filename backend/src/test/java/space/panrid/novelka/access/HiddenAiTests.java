package space.panrid.novelka.access;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.SITE_SETTING;
import static space.panrid.novelka.support.Browser.json;

import java.util.Random;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.FakeSyosetu;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 19, п. 7: while the site owner keeps AI hidden from people without шаги (the default), they
 * see none of it and the server refuses every way in; the owner and people with шаги keep it all.
 */
@IntegrationTest
class HiddenAiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HIDDEN = "Інструменти ШІ поки відкриті лише тим, у кого є шаги";

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    FakeSyosetu syosetu;

    @Autowired
    FakeModel model;

    Person owner;
    /** Let in to autotranslate by the owner, but with no шаги. */
    Person person;
    long own;

    @BeforeEach
    void theSiteOwnerAndAPersonLetInWithoutShahs() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        person = Accounts.signedIn(port, mailbox);
        assertThat(owner.browser().put("/api/admin/users/" + person.nick() + "/autotranslate", json("allowed", true)).status())
                .isBetween(200, 204);
        own = read(person.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Своє %s"}""".formatted(person.nick()))).path("editionId").asLong();
        db.deleteFrom(SITE_SETTING).where(SITE_SETTING.KEY.eq(SiteSettings.AI_HIDDEN_WITHOUT_SHAHS)).execute();
        model.reset();
    }

    @AfterEach
    void backToTheDefault() {
        db.deleteFrom(SITE_SETTING).where(SITE_SETTING.KEY.eq(SiteSettings.AI_HIDDEN_WITHOUT_SHAHS)).execute();
    }

    private String edition() {
        return "/api/studio/editions/" + own;
    }

    private String novel() {
        Random random = new Random();
        String code = "n%04d%c%c".formatted(random.nextInt(10_000), (char) ('a' + random.nextInt(26)), (char) ('a' + random.nextInt(26)));
        syosetu.add(code, new FakeSyosetu.Novel("灯台守の夜", "桜ゆき", "あらすじ。" + code, 3));
        return "https://ncode.syosetu.com/" + code + "/";
    }

    /** Every way into AI there is for a translator of their own edition. */
    private Response[] everyWayIn(Browser browser) {
        String at = edition();
        return new Response[] {
            browser.post("/api/studio/autotranslate/prepare", json("url", novel())),
            browser.get(at + "/autotranslate"),
            browser.get(at + "/source-watch"),
            browser.put(at + "/source-watch", json("watching", true)),
            browser.post(at + "/source-check", "{}"),
            browser.post(at + "/autotranslate/quote", json("kind", "translate", "to", 1)),
            browser.post(at + "/autotranslate/jobs", json("kind", "translate", "to", 1)),
            browser.get(at + "/autotranslate/jobs/1/log"),
            browser.post(at + "/autotranslate/jobs/1/resume", "{}"),
            browser.get(at + "/source-pictures"),
            browser.post(at + "/source-pictures/1", "{}"),
            browser.post(at + "/glossary/1/rewrite", json("from", "Кай", "apply", false, "ai", true)),
            browser.post(at + "/glossary/1/regender", json("apply", false)),
            browser.post(at + "/chapters/1/glossary/source", json("blockId", "b1", "translation", "Кай пішов.", "selection", "Кай")),
            browser.post(at + "/chapters/1/variants", json("blockId", "b1", "text", "Кай пішов.")),
            browser.post(at + "/chapters/1/check", "{}"),
            browser.get(at + "/illustrations/price"),
            browser.post(at + "/illustrations/prompt", json("fragment", "Маяк уночі.")),
            browser.post(at + "/illustrations", json("prompt", "A lighthouse at night", "aspect", "1:1")),
        };
    }

    @Test
    void withoutShahsNothingOfAiIsShownAndTheServerRefusesEveryWayIn() {
        JsonNode me = read(person.browser().get("/api/me"));
        assertThat(me.path("ai").asBoolean()).as("the web app hides it all").isFalse();
        assertThat(me.path("autotranslate").asBoolean()).as("let in, but AI is hidden").isTrue();

        for (Response refused : everyWayIn(person.browser())) {
            assertThat(refused.status()).as(refused.body()).isEqualTo(403);
            assertThat(refused.body()).contains(HIDDEN);
        }
        assertThat(model.calls).isEmpty();

        // What is not AI stays: the original pasted by hand, the glossary without AI, cancelling a run.
        assertThat(person.browser().get(edition() + "/originals").status()).isEqualTo(200);
        assertThat(person.browser().get(edition() + "/glossary").status()).isEqualTo(200);
        assertThat(person.browser().post(edition() + "/glossary/1/rewrite", json("from", "Кай", "apply", false, "ai", false)).status())
                .as("no such entry, but not refused as AI").isNotEqualTo(403);
        assertThat(person.browser().get("/api/studio/autotranslate/processes").status()).isEqualTo(200);
    }

    @Test
    void aProposedNovelIsTakenByHandWhenAiIsHidden() {
        Person proposer = Accounts.signedIn(port, mailbox);
        long id = read(proposer.browser().post("/api/proposals", json("url", novel()))).path("id").asLong();
        JsonNode taken = read(person.browser().post("/api/proposals/" + id + "/take", json("team", "")));
        JsonNode about = read(person.browser().get("/api/studio/editions/" + taken.path("editionId").asLong()));
        assertThat(about.path("sourceChapters").isNull()).as("nothing fetched from the original's site").isTrue();
    }

    @Test
    void withShahsAndForTheSiteOwnerAllOfItIsThere() {
        assertThat(owner.browser().post("/api/admin/users/" + person.nick() + "/shahs", json("shah", 3)).status()).isEqualTo(201);
        assertThat(read(person.browser().get("/api/me")).path("ai").asBoolean()).isTrue();
        assertThat(read(owner.browser().get("/api/me")).path("ai").asBoolean()).isTrue();

        for (Response passed : everyWayIn(person.browser())) {
            assertThat(passed.body()).doesNotContain(HIDDEN);
        }
        assertThat(person.browser().get(edition() + "/autotranslate").status()).isEqualTo(200);
        assertThat(person.browser().get(edition() + "/illustrations/price").status()).isEqualTo(200);

        long owners = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Власникове %s"}""".formatted(owner.nick()))).path("editionId").asLong();
        assertThat(owner.browser().get("/api/studio/editions/" + owners + "/illustrations/price").status()).isEqualTo(200);
        assertThat(owner.browser().get("/api/studio/editions/" + owners + "/autotranslate").status()).isEqualTo(200);
    }

    @Test
    void switchedOffItIsAsBefore() {
        JsonNode before = read(owner.browser().get("/api/admin/settings"));
        assertThat(before.path("hideAiWithoutShahs").asBoolean()).as("hidden until the owner opens it").isTrue();

        JsonNode saved = read(owner.browser().put("/api/admin/settings", json("relayInactiveMonths", before.path("relayInactiveMonths").asInt(),
                "registrationOpen", before.path("registrationOpen").asBoolean(), "adultEnabled", before.path("adultEnabled").asBoolean(),
                "hideAiWithoutShahs", false)));
        assertThat(saved.path("hideAiWithoutShahs").asBoolean()).isFalse();
        JsonNode logged = read(owner.browser().get("/api/admin/audit")).path("items").path(0);
        assertThat(logged.path("action").asString()).isEqualTo("settings");
        assertThat(logged.path("details").path("before").path("hideAiWithoutShahs").asBoolean()).isTrue();
        assertThat(logged.path("details").path("after").path("hideAiWithoutShahs").asBoolean()).isFalse();

        assertThat(read(person.browser().get("/api/me")).path("ai").asBoolean()).isTrue();
        for (Response asBefore : everyWayIn(person.browser())) {
            assertThat(asBefore.body()).doesNotContain(HIDDEN);
        }
        Response price = person.browser().get(edition() + "/illustrations/price");
        assertThat(price.status()).as("as before: drawing is for шаги").isEqualTo(400);
        assertThat(price.body()).contains("шаги");
        assertThat(person.browser().post("/api/studio/autotranslate/prepare", json("url", novel())).status())
                .as("as before: runs are for шаги").isEqualTo(400);

        // Leaving it out of the request keeps it as it is.
        JsonNode kept = read(owner.browser().put("/api/admin/settings", json("relayInactiveMonths", before.path("relayInactiveMonths").asInt(),
                "registrationOpen", before.path("registrationOpen").asBoolean(), "adultEnabled", before.path("adultEnabled").asBoolean())));
        assertThat(kept.path("hideAiWithoutShahs").asBoolean()).isFalse();
        assertThat(person.browser().get("/api/admin/settings").status()).isEqualTo(403);
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
