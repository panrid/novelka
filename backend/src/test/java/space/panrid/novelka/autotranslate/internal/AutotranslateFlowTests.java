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

        JsonNode overview = read(owner.browser().get("/api/studio/editions/" + edition + "/autotranslate"));
        assertThat(overview.path("sourceChapters").asInt()).isEqualTo(5);
        assertThat(overview.path("nextNumber").asInt()).isEqualTo(1);
        JsonNode quote = read(owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/quote", json("kind", "translate", "to", 3)));
        assertThat(quote.path("shah").asInt()).as("three short chapters, a шаг each").isEqualTo(3);
        assertThat(quote.path("usd").decimalValue()).isEqualByComparingTo("0.11");
        assertThat(quote.path("expectedUsd").decimalValue()).as("what the models themselves should cost").isPositive();
        assertThat(quote.path("estimated").asBoolean()).isTrue();
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
        JsonNode history = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/2/revisions")).path(0);
        assertThat(history.path("blocksChanged").asInt()).as("the machine version shows its own size").isEqualTo(6);
        assertThat(history.path("charsChanged").asInt()).isPositive();

        JsonNode glossary = read(owner.browser().get("/api/studio/editions/" + edition + "/glossary")).path("items");
        assertThat(glossary).as("the novel's own title and one name").hasSize(2);
        assertThat(glossary.toString()).contains("Юкі").contains("Ліхтарник із туману").doesNotContain("ユキ");

        JsonNode next = read(owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/quote", json("to", 5)));
        assertThat(next.path("from").asInt()).as("translated chapters are not touched").isEqualTo(4);
        assertThat(next.path("estimated").asBoolean()).isTrue();

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

        JsonNode quote = read(owner.browser().post(base + "/autotranslate/quote", json("kind", "analyze", "to", 4)));
        assertThat(quote.path("kind").asString()).isEqualTo("analyze");
        assertThat(quote.path("shah").asInt()).as("a quarter of a шаг per chapter").isEqualTo(1);
        assertThat(owner.browser().post(base + "/autotranslate/jobs", json("to", 4, "kind", "analyze")).status()).isEqualTo(201);
        worker.drain();

        assertThat(model.calls).containsOnly("glossary").hasSize(4);
        JsonNode overview = read(owner.browser().get(base + "/autotranslate"));
        assertThat(overview.path("publishedChapters").asInt()).as("nothing translated yet").isZero();
        assertThat(overview.path("lastAnalyzed").asInt()).isEqualTo(4);
        assertThat(read(owner.browser().post(base + "/autotranslate/quote", json("to", 6))).path("unanalyzed").asInt())
                .as("5 and 6 have no analysis").isEqualTo(2);
        assertThat(owner.browser().post(base + "/autotranslate/quote", json("kind", "analyze", "from", 1, "to", 4)).body())
                .as("done chapters are skipped; none left means say so").contains("вже проаналізовано");

        JsonNode titles = read(owner.browser().get(base + "/analysis")).path("items");
        assertThat(titles).extracting(item -> item.path("label").asString(null)).containsExactly("0", "1", "1.1", "");
        assertThat(titles.path(0).path("title").asString()).isEqualTo("Світло");
        assertThat(titles.path(3).path("title").asString()).isEqualTo("Інтерлюдія");
        assertThat(owner.browser().put(base + "/analysis/1", json("title", "Пролог", "label", "0")).status()).isEqualTo(200);
        assertThat(owner.browser().put(base + "/analysis/2", json("title", "Ліхтар", "label", "1,5")).status()).isEqualTo(200);
        assertThat(owner.browser().put(base + "/analysis/2", json("title", "Ліхтар", "label", "перша")).status()).isEqualTo(400);

        JsonNode fresh = read(owner.browser().get(base + "/glossary?status=new"));
        assertThat(fresh.path("counts").path("new").asInt()).isEqualTo(fresh.path("total").asInt());
        java.util.List<Long> ids = new java.util.ArrayList<>();
        fresh.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        read(owner.browser().post(base + "/glossary/status", json("ids", ids, "status", "approved")));
        assertThat(read(owner.browser().get(base + "/glossary?status=new")).path("total").asInt()).isZero();

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
    void theOwnersOpenTabHearsTheJobMove() throws InterruptedException {
        long edition = prepare();
        var tab = owner.browser().stream("/api/events");
        owner.browser().post("/api/studio/editions/" + edition + "/autotranslate/jobs", json("to", 1));
        worker.drain();
        boolean heard = false;
        long deadline = System.currentTimeMillis() + 5_000;
        while (!heard && System.currentTimeMillis() < deadline) {
            String line = tab.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            heard = "event:job".equals(line);
        }
        assertThat(heard).isTrue();
    }

    @Test
    void chaptersAreDoneAgainWithAnotherModelAndARangeSkipsWhatIsDone() {
        long edition = prepare();
        String base = "/api/studio/editions/" + edition;
        owner.browser().post(base + "/autotranslate/jobs", json("to", 2));
        worker.drain();
        String slug = read(owner.browser().get(base)).path("novelSlug").asString();

        assertThat(owner.browser().post(base + "/autotranslate/quote", json("from", 1, "to", 2)).status())
                .as("nothing left to do without «redo»").isEqualTo(400);
        JsonNode models = read(owner.browser().get("/api/studio/autotranslate/models?q=better&chars=6000"));
        assertThat(models).singleElement().satisfies(model -> {
            assertThat(model.path("id").asString()).isEqualTo("fake/better");
            assertThat(model.path("chapterUsd").decimalValue()).isPositive();
        });
        assertThat(read(owner.browser().get("/api/studio/autotranslate/models?output=image"))).extracting(m -> m.path("id").asString())
                .containsExactlyInAnyOrder("fake/painter", "fake/drawer");
        assertThat(read(owner.browser().get("/api/studio/autotranslate/models"))).as("the whole catalogue, not a first page").hasSize(32);
        assertThat(read(owner.browser().get("/api/studio/autotranslate/models?q=gpt%20mini"))).extracting(m -> m.path("id").asString())
                .as("every word, in any order").containsExactly("openai/gpt-4.1-mini");
        double wholeChapter = models.get(0).path("chapterUsd").asDouble();
        double translation = read(owner.browser().get("/api/studio/autotranslate/models?q=better&chars=6000&stage=translate"))
                .get(0).path("chapterUsd").asDouble();
        assertThat(translation).as("the price of the stage being chosen").isPositive().isLessThan(wholeChapter);

        String plan = """
                {"kind":"translate","from":1,"to":2,"redo":true,"models":{"translate":"fake/better","proofreadEnabled":false}}""";
        JsonNode quote = read(owner.browser().post(base + "/autotranslate/quote", plan));
        assertThat(quote.path("chapters").asInt()).isEqualTo(2);
        assertThat(quote.path("translateModel").path("model").asString()).isEqualTo("fake/better");
        JsonNode usual = read(owner.browser().post(base + "/autotranslate/quote", """
                {"kind":"translate","from":1,"to":2,"redo":true,"models":{"proofreadEnabled":false}}"""));
        assertThat(quote.path("shah").asInt()).as("a five times dearer model takes more шаги, not a refusal")
                .isGreaterThan(usual.path("shah").asInt() * 2);
        assertThat(quote.path("proofreadModel").path("enabled").asBoolean()).isFalse();
        assertThat(owner.browser().post(base + "/autotranslate/quote", """
                {"to":2,"from":1,"redo":true,"models":{"translate":"nobody/none"}}""").status()).isEqualTo(400);
        assertThat(owner.browser().post(base + "/autotranslate/quote", """
                {"to":2,"from":1,"redo":true,"models":{"translate":"fake/plain"}}""").status())
                .as("a model without structured answers is refused before it costs anything").isEqualTo(400);
        assertThat(read(owner.browser().get("/api/studio/autotranslate/models?q=plain"))).as("and not offered").isEmpty();

        model.reset();
        assertThat(owner.browser().post(base + "/autotranslate/jobs", plan).status()).isEqualTo(201);
        worker.drain();
        assertThat(model.calls).as("new answers from the new model, analysis kept").containsExactly("translation", "translation");
        assertThat(db.select(AI_CALL.MODEL).from(AI_CALL).where(AI_CALL.JOB_ID.eq(jobId(edition)), AI_CALL.STAGE.eq("translate"))
                .fetch(AI_CALL.MODEL)).containsOnly("fake/better");
        assertThat(model.requests).as("a model that takes no temperature does not get one")
                .allSatisfy(request -> assertThat(request.has("temperature")).isFalse());
        assertThat(read(new Browser(port).get("/api/novels/" + slug + "/chapters/1")).path("blocks").toString())
                .as("no proofreading this time").doesNotContain("✓");

        model.reset();
        owner.browser().post(base + "/autotranslate/jobs", json("from", 4, "to", 5));
        worker.drain();
        JsonNode chapters = read(new Browser(port).get("/api/novels/" + slug + "/chapters")).path("items");
        assertThat(chapters).extracting(row -> row.path("number").asInt()).containsExactly(1, 2, 4, 5);

        JsonNode processes = read(owner.browser().get("/api/studio/autotranslate/processes"));
        assertThat(processes).hasSizeGreaterThanOrEqualTo(3);
        assertThat(processes.path(0).path("slug").asString()).isEqualTo(slug);
        assertThat(processes.path(0).path("job").path("from").asInt()).isEqualTo(4);
    }

    @Test
    void theGlossaryIsReviewedPageByPageAndRejectedEntriesStayOut() {
        long edition = prepare();
        String base = "/api/studio/editions/" + edition;
        owner.browser().post(base + "/autotranslate/jobs", json("kind", "analyze", "to", 1));
        worker.drain();
        JsonNode page = read(owner.browser().get(base + "/glossary?sort=alpha"));
        assertThat(page.path("items")).extracting(item -> item.path("ukrainian").asString()).containsExactly("Ліхтарник із туману", "Юкі");
        assertThat(page.path("chapters")).extracting(JsonNode::asInt).containsExactly(1);
        assertThat(page.path("labels").path("1").asString()).as("the number readers will see").isEqualTo("1");
        long yuki = page.path("items").path(1).path("id").asLong();
        assertThat(read(owner.browser().get(base + "/glossary?chapter=1")).path("items")).singleElement()
                .satisfies(item -> assertThat(item.path("ukrainian").asString()).isEqualTo("Юкі"));

        read(owner.browser().post(base + "/glossary/status", json("ids", java.util.List.of(yuki), "status", "rejected")));
        assertThat(read(owner.browser().get(base + "/glossary?status=rejected")).path("items").path(0).path("id").asLong()).isEqualTo(yuki);
        model.reset();
        owner.browser().post(base + "/autotranslate/jobs", json("to", 1));
        worker.drain();
        String slug = read(owner.browser().get(base)).path("novelSlug").asString();
        assertThat(read(new Browser(port).get("/api/novels/" + slug + "/chapters/1")).path("blocks").toString())
                .as("a rejected name is not in the prompt").doesNotContain("Юкі:");
        assertThat(owner.browser().post(base + "/glossary/status", json("ids", java.util.List.of(yuki), "status", "maybe")).status())
                .isEqualTo(400);
    }

    @Test
    void withoutShahsOnlyTheSiteOwnerMayUseIt() {
        Person reader = Accounts.signedIn(port, mailbox);
        Response refused = reader.browser().post("/api/studio/autotranslate/prepare", json("url", "https://ncode.syosetu.com/" + code + "/"));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("шаги");
        assertThat(reader.browser().get("/api/studio/autotranslate/wallet").status()).isEqualTo(403);
        assertThat(reader.browser().get("/api/studio/autotranslate/models").status()).as("the site picks models for people").isEqualTo(403);
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
