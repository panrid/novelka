package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.AI_CALL;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.support.Browser.json;

import java.util.Random;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.Eventually;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.FakeSyosetu;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Рішення 29: the owner grants шаги, a person spends them on runs charged what they really cost. */
@IntegrationTest
class PersonalRunsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @Autowired
    Worker worker;

    Person owner;
    Person reader;

    @BeforeEach
    void theSiteOwnerAndAReader() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        reader = Accounts.signedIn(port, mailbox);
        model.reset();
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
    }

    private String novel(int chapters) {
        Random random = new Random();
        String code = "n%04d%c%c".formatted(random.nextInt(10_000), (char) ('a' + random.nextInt(26)), (char) ('a' + random.nextInt(26)));
        // Its own title: the answers saved for other tests' novel must not be reused here.
        syosetu.add(code, new FakeSyosetu.Novel("霧の港の記録", "森あおい", "港の話。", chapters));
        return "https://ncode.syosetu.com/" + code + "/";
    }

    private void grant(int shah) {
        assertThat(owner.browser().post("/api/admin/users/" + reader.nick() + "/shahs", json("shah", shah)).status()).isEqualTo(201);
    }

    private JsonNode mine() {
        return read(reader.browser().get("/api/me/shahs"));
    }

    @Test
    void theOwnerGrantsShahsAndARunIsChargedWhatItCostRoundedUp() {
        assertThat(reader.browser().post("/api/admin/users/" + owner.nick() + "/shahs", json("shah", 5)).status())
                .as("only the site owner grants").isEqualTo(403);
        assertThat(owner.browser().post("/api/admin/users/" + reader.nick() + "/shahs", json("shah", 0)).status()).isEqualTo(400);
        assertThat(owner.browser().post("/api/admin/users/" + reader.nick() + "/shahs", json("shah", 10, "note", "на пробу")).status())
                .isEqualTo(201);

        JsonNode mine = mine();
        assertThat(mine.path("available").asInt()).isEqualTo(10);
        assertThat(mine.path("usdPerShah").decimalValue()).isEqualByComparingTo("0.070");
        assertThat(mine.path("history").path(0).path("kind").asString()).isEqualTo("grant");
        assertThat(mine.path("history").path(0).path("what").asString()).isEqualTo("на пробу");
        JsonNode told = Eventually.eventually(() -> read(reader.browser().get("/api/notifications")),
                page -> page.toString().contains("shahs_granted")).path("items");
        assertThat(told).anySatisfy(item -> assertThat(item.path("payload").path("shah").asInt()).isEqualTo(10));

        long edition = read(reader.browser().post("/api/studio/autotranslate/prepare", json("url", novel(3)))).path("editionId").asLong();
        String base = "/api/studio/editions/" + edition + "/autotranslate";
        JsonNode overview = read(reader.browser().get(base));
        assertThat(overview.path("personal").asBoolean()).isTrue();
        assertThat(overview.path("balance").path("shah").asInt()).isEqualTo(10);
        assertThat(reader.browser().post(base + "/quote", """
                {"to":3,"models":{"translate":"fake/better"}}""").status()).as("the site picks the models").isEqualTo(400);
        JsonNode quote = read(reader.browser().post(base + "/quote", json("to", 3)));
        int reserve = quote.path("reserveShah").asInt();
        assertThat(quote.path("shah").asInt()).isPositive();
        assertThat(reserve).isGreaterThanOrEqualTo(quote.path("shah").asInt());

        assertThat(reader.browser().post(base + "/jobs", json("to", 3)).status()).isEqualTo(201);
        assertThat(mine().path("available").asInt()).as("held while it runs").isEqualTo(10 - reserve);
        assertThat(mine().path("reserved").asInt()).isEqualTo(reserve);
        assertThat(mine().path("running").path(0).path("what").asString()).startsWith("Автопереклад «");
        worker.drain();

        JsonNode job = read(reader.browser().get(base)).path("jobs").path(0);
        assertThat(job.path("state").asString()).isEqualTo("done");
        assertThat(job.path("personal").asBoolean()).isTrue();
        long spent = db.select(DSL.sum(AI_CALL.COST_ACTUAL_MUSD).cast(Long.class)).from(AI_CALL)
                .where(AI_CALL.JOB_ID.eq(job.path("id").asLong())).fetchOne(0, Long.class);
        int charged = (int) Math.ceil(spent / 70_000.0);
        assertThat(charged).as("nine calls at a tenth of a cent: under one шаг, charged as one").isEqualTo(1);
        assertThat(job.path("chargedShah").asInt()).isEqualTo(charged);
        mine = mine();
        assertThat(mine.path("available").asInt()).isEqualTo(10 - charged);
        assertThat(mine.path("reserved").asInt()).isZero();
        assertThat(mine.path("history").path(0).path("kind").asString()).isEqualTo("charge");
        assertThat(mine.path("history").path(0).path("amount").asInt()).isEqualTo(charged);
        assertThat(read(reader.browser().get("/api/studio/autotranslate/processes"))).hasSize(1);
    }

    @Test
    void aCancelledRunReturnsWhatItHeldAndBalancesNeverGoBelowZero() {
        grant(1);
        long edition = read(reader.browser().post("/api/studio/autotranslate/prepare", json("url", novel(2)))).path("editionId").asLong();
        long other = read(reader.browser().post("/api/studio/autotranslate/prepare", json("url", novel(2)))).path("editionId").asLong();
        String base = "/api/studio/editions/" + edition + "/autotranslate";
        int reserve = read(reader.browser().post(base + "/quote", json("to", 2))).path("reserveShah").asInt();
        if (reserve > 1) {
            grant(reserve - 1);
        }
        long jobId = read(reader.browser().post(base + "/jobs", json("to", 2))).path("id").asLong();
        assertThat(mine().path("available").asInt()).isZero();

        Response short_ = reader.browser().post("/api/studio/editions/" + other + "/autotranslate/jobs", json("to", 1));
        assertThat(short_.status()).as("nothing left to hold").isEqualTo(400);
        assertThat(short_.body()).contains("Не вистачає шагів");

        assertThat(reader.browser().post(base + "/jobs/" + jobId + "/cancel", "{}").status()).isEqualTo(200);
        JsonNode mine = mine();
        assertThat(mine.path("available").asInt()).as("nothing was spent, nothing is charged").isEqualTo(Math.max(1, reserve));
        assertThat(mine.path("reserved").asInt()).isZero();
        assertThat(db.select(JOB.CHARGED_SHAH).from(JOB).where(JOB.ID.eq(jobId)).fetchSingle().value1()).isZero();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
