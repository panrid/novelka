package space.panrid.novelka.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;

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
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 19, п. 9: a translation starts with «Маю право перекладати цей текст», and a rights holder's
 * complaint hides it at once until moderators look.
 */
@IntegrationTest
class RightsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person owner;
    Person translator;

    @BeforeEach
    void theSiteOwnerAndATranslator() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        translator = Accounts.signedIn(port, mailbox);
    }

    @Test
    void theTeamSaysItMayTranslateAndTheRightsHolderHidesTheTranslation() {
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[],"rightsConfirmed":true}""")).path("editionId").asLong();
        assertThat(db.select(EDITION.RIGHTS_CONFIRMED_AT).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1()).isNotNull();
        long own = read(translator.browser().post("/api/studio/editions", """
                {"kind":"original","title":"Мій твір","description":[],"tags":[],"rightsConfirmed":true}""")).path("editionId").asLong();
        assertThat(db.select(EDITION.RIGHTS_CONFIRMED_AT).from(EDITION).where(EDITION.ID.eq(own)).fetchSingle().value1())
                .as("an own work needs no promise").isNull();

        String complaint = JSON.writeValueAsString(Map.of("editionId", edition, "statement", "Я видавець «Mother of Learning», дозволу не давав.",
                "contact", "rights@example.com"));
        assertThat(new Browser(port).post("/api/rights-complaints", complaint).status()).isEqualTo(401);
        Person holder = Accounts.signedIn(port, mailbox);
        assertThat(holder.browser().post("/api/rights-complaints", JSON.writeValueAsString(Map.of("editionId", edition,
                "statement", " ", "contact", "x"))).status()).isEqualTo(400);
        assertThat(holder.browser().post("/api/rights-complaints", complaint).status()).isEqualTo(201);

        assertThat(db.select(EDITION.HIDDEN_REASON).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1())
                .as("hidden at once").isEqualTo("Скарга правовласника");
        JsonNode reports = read(owner.browser().get("/api/admin/reports"));
        assertThat(reports).anySatisfy(report -> {
            assertThat(report.path("targetId").asLong()).isEqualTo(edition);
            assertThat(report.path("reasons").toString()).contains("Скарга правовласника").contains("rights@example.com");
        });
        assertThat(owner.browser().post("/api/admin/hidden/edition/" + edition + "/restore", "{}").status()).isBetween(200, 204);
        assertThat(db.select(EDITION.HIDDEN_AT).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1())
                .as("moderators bring it back").isNull();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
