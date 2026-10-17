package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.support.Browser.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.jooq.tables.records.JobStepRecord;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.FakeSyosetu;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Several chapters translate at once: never two of one novel, and the next one goes to whoever
 * waited longest — the person first, then their run.
 */
@IntegrationTest
class ParallelWorkerTests {

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

    @BeforeEach
    void theSiteOwner() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        model.reset();
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
    }

    @AfterEach
    void nothingLeftRunning() {
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
        db.update(JOB_STEP).set(JOB_STEP.STATE, "cancelled").where(JOB_STEP.STATE.in("pending", "running")).execute();
    }

    @Test
    void differentNovelsGoTogetherButANovelHasOneChapterInWork() {
        long first = start(prepare(), 3);
        long second = start(prepare(), 3);

        JobStepRecord a = worker.claim();
        JobStepRecord b = worker.claim();
        assertThat(List.of(a.getJobId(), b.getJobId())).containsExactlyInAnyOrder(first, second);
        assertThat(worker.claim()).as("each run waits for its chapter in work").isNull();

        // A second translation of the first novel: it shares the glossary, so it waits too.
        // (A novel has one edition per team, so this one moves to another team.)
        long edition = prepare();
        long sameNovel = start(edition, 2);
        String handle = "parallel" + System.nanoTime();
        long otherTeam = db.insertInto(TEAM, TEAM.HANDLE, TEAM.HANDLE_KEY, TEAM.OWNER_ID)
                .select(db.select(DSL.val(handle), DSL.val(handle), TEAM.OWNER_ID).from(TEAM).where(TEAM.ID.eq(teamOf(edition))))
                .returning(TEAM.ID).fetchSingle().getId();
        db.update(EDITION).set(EDITION.NOVEL_ID, novelOf(editionOf(first))).set(EDITION.TEAM_ID, otherTeam)
                .where(EDITION.ID.eq(edition)).execute();
        assertThat(worker.claim()).as("the novel already has a chapter in work").isNull();

        done(a.getJobId() == first ? a : b);
        JobStepRecord next = worker.claim();
        assertThat(next).isNotNull();
        assertThat(List.of(first, sameNovel)).contains(next.getJobId());
        assertThat(next.getChapterNumber()).as("chapters of a run stay in order")
                .isEqualTo(next.getJobId() == first ? 2 : 1);
    }

    @Test
    void theNextChapterGoesToWhoeverWaitedLongestPersonFirst() {
        long a = start(prepare(), 4);
        long b = start(prepare(), 4);
        long c = start(prepare(), 4);
        Person other = Accounts.signedIn(port, mailbox);
        long otherId = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(other.nick())).fetchSingle().value1();
        db.update(JOB).set(JOB.REQUESTED_BY, otherId).where(JOB.ID.eq(c)).execute();

        List<Long> served = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            JobStepRecord step = worker.claim();
            served.add(step.getJobId());
            done(step);
        }
        assertThat(served).as("the owner's two runs share their turns; the other person gets every other chapter")
                .containsExactly(a, c, b, c, a);
    }

    @Test
    void twoWorkersTranslateTwoNovelsAtOnce() throws Exception {
        long first = start(prepare(), 2);
        long second = start(prepare(), 2);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> ran = workers.invokeAll(List.<Callable<Integer>>of(worker::drain, worker::drain));
            assertThat(ran.get(0).get() + ran.get(1).get()).isEqualTo(4);
        } finally {
            workers.shutdown();
        }
        assertThat(db.select(JOB.STATE).from(JOB).where(JOB.ID.in(first, second)).fetch(JOB.STATE)).containsOnly("done");
        assertThat(db.fetchCount(JOB_STEP, JOB_STEP.JOB_ID.in(first, second), JOB_STEP.STATE.eq("done"))).isEqualTo(4);
        assertThat(db.fetchCount(JOB_STEP, JOB_STEP.JOB_ID.in(first, second), JOB_STEP.ATTEMPTS.gt(1)))
                .as("no chapter was taken twice").isZero();
    }

    @Test
    void theSiteOwnerSetsHowManyChaptersGoAtOnce() {
        assertThat(read(owner.browser().get("/api/studio/autotranslate/wallet")).path("parallel").asInt()).isEqualTo(3);
        assertThat(owner.browser().put("/api/studio/autotranslate/parallel", json("chapters", 9)).status()).isEqualTo(400);
        assertThat(owner.browser().put("/api/studio/autotranslate/parallel", json("chapters", 2)).status()).isBetween(200, 204);
        assertThat(read(owner.browser().get("/api/studio/autotranslate/wallet")).path("parallel").asInt()).isEqualTo(2);
        JsonNode logged = read(owner.browser().get("/api/admin/audit")).path("items").path(0);
        assertThat(logged.path("action").asString()).isEqualTo("autotranslate_parallel");
        assertThat(logged.path("details").path("after").asInt()).isEqualTo(2);

        Person reader = Accounts.signedIn(port, mailbox);
        assertThat(reader.browser().put("/api/studio/autotranslate/parallel", json("chapters", 5)).status()).isEqualTo(403);
        owner.browser().put("/api/studio/autotranslate/parallel", json("chapters", 3));
    }

    private long prepare() {
        Random random = new Random();
        String code = "n%04d%c%c".formatted(random.nextInt(10_000), (char) ('a' + random.nextInt(26)), (char) ('a' + random.nextInt(26)));
        syosetu.add(code, new FakeSyosetu.Novel("並行の灯 " + code, "桜ゆき", "あらすじ。", 4));
        return read(owner.browser().post("/api/studio/autotranslate/prepare",
                json("url", "https://ncode.syosetu.com/" + code + "/"))).path("editionId").asLong();
    }

    private long start(long edition, int chapters) {
        return read(owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", chapters))).path("id").asLong();
    }

    private void done(JobStepRecord step) {
        db.update(JOB_STEP).set(JOB_STEP.STATE, "done").where(JOB_STEP.ID.eq(step.getId())).execute();
    }

    private long editionOf(long job) {
        return db.select(JOB.EDITION_ID).from(JOB).where(JOB.ID.eq(job)).fetchSingle().value1();
    }

    private long teamOf(long edition) {
        return db.select(EDITION.TEAM_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1();
    }

    private long novelOf(long edition) {
        return db.select(EDITION.NOVEL_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
