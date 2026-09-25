package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.AI_CALL;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;
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
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.FakeModel.Trouble;
import space.panrid.novelka.support.FakeSyosetu;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class AutotranslateFlowTests {

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
    String code;

    @BeforeEach
    void theSiteOwnerAndANovelOnSyosetu() {
        owner = Accounts.signedIn(port, mailbox);
        // The site has one owner: the previous test's steps down.
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        Random random = new Random();
        code = "n%04d%c%c".formatted(random.nextInt(10_000), (char) ('a' + random.nextInt(26)), (char) ('a' + random.nextInt(26)));
        syosetu.add(code, new FakeSyosetu.Novel("灯台守の夜", "桜ゆき", "あらすじ。", 5));
        model.reset();
        // Jobs left by other tests must not take the model's troubles meant for this one.
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
    }

    private long prepare() {
        return read(owner.browser().post("/api/studio/autotranslate/prepare",
                json("url", "https://ncode.syosetu.com/" + code + "/"))).path("editionId").asLong();
    }

    @Test
    void theOwnerPastesALinkSeesThePriceAndGetsPublishedChapters() {
        long edition = prepare();
        JsonNode about = read(owner.browser().get("/api/studio/editions/" + edition));
        assertThat(about.toString()).as("only Ukrainian reaches the site").doesNotContain("灯台").contains("Ліхтарник із туману");
        assertThat(prepare()).as("the same link again reuses the edition").isEqualTo(edition);

        JsonNode overview = read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate?to=3"));
        assertThat(overview.path("sourceChapters").asInt()).isEqualTo(5);
        assertThat(overview.path("nextNumber").asInt()).isEqualTo(1);
        assertThat(overview.path("quote").path("shah").asInt()).as("three short chapters, a шаг each").isEqualTo(3);
        assertThat(overview.path("quote").path("usd").decimalValue()).isEqualByComparingTo("0.11");
        assertThat(overview.path("quote").path("estimated").asBoolean()).isTrue();
        assertThat(overview.path("balance").path("usd").decimalValue()).isEqualByComparingTo("7.50");
        assertThat(overview.path("balance").path("shah").asInt()).isEqualTo(208);

        Response started = owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", 3));
        assertThat(started.status()).isEqualTo(201);
        assertThat(owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", 4)).status())
                .as("one job at a time").isEqualTo(409);
        worker.drain();

        JsonNode job = read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("jobs").path(0);
        assertThat(job.path("state").asString()).isEqualTo("done");
        assertThat(job.path("done").asInt()).isEqualTo(3);
        // metadata + 3 chapters × (analyze, translate, proofread)
        assertThat(model.calls).hasSize(10);

        String slug = read(owner.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        JsonNode chapter = read(new Browser(port).get("/api/novels/" + slug + "/chapters/2"));
        assertThat(chapter.path("title").asString()).as("the number is the site's, not the title's").isEqualTo("Світло");
        assertThat(chapter.path("label").asString()).isEqualTo("2");
        assertThat(chapter.path("blocks").toString()).doesNotContain("雪").contains("Юкі: переклад s2 ✓");
        assertThat(chapter.path("blocks")).extracting(block -> block.path("type").asString())
                .containsExactly("preface", "paragraph", "paragraph", "separator", "paragraph", "afterword");

        JsonNode glossary = read(owner.browser().get("/api/studio/editions/" + edition + "/glossary"));
        assertThat(glossary).as("the novel's own title and one name").hasSize(2);
        assertThat(glossary.toString()).contains("Юкі").contains("Ліхтарник із туману").doesNotContain("ユキ");

        JsonNode next = read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate?to=5"));
        assertThat(next.path("quote").path("from").asInt()).as("translated chapters are not touched").isEqualTo(4);
        assertThat(next.path("quote").path("estimated").asBoolean()).isTrue();

        JsonNode wallet = read(owner.browser().get("/api/studio/autotranslate/wallet"));
        assertThat(wallet.path("report").path(0).path("chapters").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(wallet.path("report").path(0).path("avgChapter").decimalValue()).isEqualByComparingTo("0.003");
    }

    @Test
    void aRateLimitIsRetriedByItselfAndAnIncompleteAnswerIsAskedAgain() {
        long edition = prepare();
        owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", 1));
        model.troubleNext(Trouble.RATE_LIMIT);
        worker.drain();
        assertThat(db.select(JOB_STEP.STATE, JOB_STEP.ERROR_REASON).from(JOB_STEP)
                .where(JOB_STEP.JOB_ID.eq(jobId(edition))).fetchOne().into(String[].class))
                .containsExactly("pending", "retry");

        db.update(JOB_STEP).set(JOB_STEP.NOT_BEFORE, DSL.currentOffsetDateTime()).where(JOB_STEP.JOB_ID.eq(jobId(edition))).execute();
        model.troubleNext(Trouble.NONE, Trouble.DROP_BLOCK);
        worker.drain();
        assertThat(read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("jobs").path(0)
                .path("state").asString()).isEqualTo("done");
        assertThat(model.calls).as("analyze after 429, translate, the left-out line alone, proofread")
                .containsExactly("glossary", "glossary", "translation", "translation", "proofread");
        assertThat(model.translatedBlocks.getLast()).as("only the missing paragraph is asked again").isEqualTo(1);
        String slug = read(owner.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        assertThat(read(new Browser(port).get("/api/novels/" + slug + "/chapters/1")).path("blocks")).hasSize(6);
    }

    @Test
    void aLostAnswerStopsTheJobUntilTheOwnerAllowsANewAttempt() {
        long edition = prepare();
        owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", 2));
        model.troubleNext(Trouble.LOST);
        worker.drain();

        JsonNode job = read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("jobs").path(0);
        assertThat(job.path("state").asString()).isEqualTo("failed");
        assertThat(job.path("error").asString()).contains("загубилася").contains("Продовжити");
        assertThat(db.fetchCount(AI_CALL, AI_CALL.JOB_ID.eq(jobId(edition)).and(AI_CALL.STATE.eq("uncertain")))).isEqualTo(1);

        assertThat(owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs/" + jobId(edition) + "/resume", "{}")
                .status()).isEqualTo(200);
        worker.drain();
        assertThat(read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("jobs").path(0)
                .path("state").asString()).isEqualTo("done");
    }

    @Test
    void aModelThatKeepsAnsweringWithForeignParagraphsStopsTheJobWithAClearReason() {
        long edition = prepare();
        owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", 1));
        model.troubleNext(Trouble.NONE, Trouble.GARBLE, Trouble.GARBLE, Trouble.GARBLE);
        worker.drain();
        JsonNode job = read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("jobs").path(0);
        assertThat(job.path("state").asString()).isEqualTo("failed");
        assertThat(job.path("error").asString()).contains("неповну відповідь").contains("переклад");

        assertThat(owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs/" + jobId(edition) + "/cancel", "{}")
                .status()).isEqualTo(200);
        assertThat(owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", 1)).status())
                .as("a new job after cancelling").isEqualTo(201);
    }

    @Test
    void analysisRunsFirstSoTheGlossaryAndTitlesAreCheckedBeforeTranslating() {
        syosetu.add(code, new FakeSyosetu.Novel("灯台守の夜", "桜ゆき", "あらすじ。", 6, java.util.Map.of(
                1, "第0話　プロローグ", 2, "第1話　灯り", 3, "第1.1話　続き", 4, "閑話　ある夜")));
        long edition = prepare();
        String base = "/api/studio/editions/" + edition;

        JsonNode quote = read(owner.browser().get(base + "/autotranslate?to=4&kind=analyze")).path("quote");
        assertThat(quote.path("kind").asString()).isEqualTo("analyze");
        assertThat(quote.path("shah").asInt()).as("a quarter of a шаг per chapter").isEqualTo(1);
        assertThat(owner.browser().post(base + "/autotranslate/jobs", json("to", 4, "kind", "analyze")).status()).isEqualTo(201);
        worker.drain();

        assertThat(model.calls).containsOnly("glossary").hasSize(4);
        JsonNode overview = read(owner.browser().get(base + "/autotranslate?to=6"));
        assertThat(overview.path("publishedChapters").asInt()).as("nothing translated yet").isZero();
        assertThat(overview.path("lastAnalyzed").asInt()).isEqualTo(4);
        assertThat(overview.path("quote").path("unanalyzed").asInt()).as("5 and 6 have no analysis").isEqualTo(2);

        JsonNode titles = read(owner.browser().get(base + "/analysis"));
        assertThat(titles).extracting(item -> item.path("label").asString(null)).containsExactly("0", "1", "1.1", "");
        assertThat(titles.path(0).path("title").asString()).isEqualTo("Світло");
        assertThat(titles.path(3).path("title").asString()).isEqualTo("Інтерлюдія");
        assertThat(owner.browser().put(base + "/analysis/1", json("title", "Пролог", "label", "0")).status()).isEqualTo(200);
        assertThat(owner.browser().put(base + "/analysis/2", json("title", "Ліхтар", "label", "1,5")).status()).isEqualTo(200);
        assertThat(owner.browser().put(base + "/analysis/2", json("title", "Ліхтар", "label", "перша")).status()).isEqualTo(400);

        JsonNode glossary = read(owner.browser().get(base + "/glossary"));
        assertThat(glossary.path(0).path("manual").asBoolean()).as("not checked yet").isFalse();
        owner.browser().post(base + "/glossary/checked", "{}");
        assertThat(read(owner.browser().get(base + "/glossary")).path(0).path("manual").asBoolean()).isTrue();

        model.reset();
        owner.browser().post(base + "/autotranslate/jobs", json("to", 4));
        worker.drain();
        assertThat(model.calls).as("analysis is not paid twice").doesNotContain("glossary").hasSize(8);

        String slug = read(owner.browser().get(base)).path("novelSlug").asString();
        JsonNode list = read(new Browser(port).get("/api/novels/" + slug + "/chapters")).path("items");
        assertThat(list).extracting(row -> row.path("label").asString(null) + " " + row.path("title").asString())
                .containsExactly("0 Пролог", "1.5 Ліхтар", "1.1 Світло", " Інтерлюдія");
    }

    @Test
    void onlyTheSiteOwnerMayUseIt() {
        Person reader = Accounts.signedIn(port, mailbox);
        assertThat(reader.browser().post("/api/studio/autotranslate/prepare",
                json("url", "https://ncode.syosetu.com/" + code + "/")).status()).isEqualTo(403);
        assertThat(reader.browser().get("/api/studio/autotranslate/wallet").status()).isEqualTo(403);
        assertThat(owner.browser().post("/api/studio/autotranslate/prepare", json("url", "https://example.com/")).status())
                .isEqualTo(400);
    }

    private long jobId(long edition) {
        return read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("jobs").path(0).path("id").asLong();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
