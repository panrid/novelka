package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.API_TOKEN;
import static space.panrid.novelka.jooq.Tables.API_TOKEN_WRITE;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 22.3: the Studio's API, {@code /api/v1/studio}, with tokens — the same rules as the Studio's
 * pages: scopes narrow it, the original is the team's, AI is for those with шаги and costs them,
 * and the whole way from a pasted original to a published chapter works without the site.
 */
@IntegrationTest
class StudioApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

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
    void scopesDecideWhichPartOfTheStudioATokenReaches() {
        String base = translation(translator);
        String reading = token(translator, List.of("read"));
        Response refused = bearer("GET", "/api/v1/studio/translations", reading, null);
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.body()).contains("«studio»");

        String studio = token(translator, List.of("studio"));
        JsonNode mine = read(bearer("GET", "/api/v1/studio/translations", studio, null));
        assertThat(mine.path("items")).anySatisfy(item -> assertThat(base).contains("/" + item.path("novel").asString() + "/"));
        assertThat(read(bearer("GET", base, studio, null)).path("role").asString()).isEqualTo("owner");
        Response noRuns = bearer("POST", base + "/runs/estimate", studio, json("to", 1));
        assertThat(noRuns.status()).as("AI that spends шаги needs its own right").isEqualTo(403);
        assertThat(noRuns.body()).contains("«translate»");
        assertThat(bearer("POST", base + "/suggestions", studio, "{\"changes\":[]}").status())
                .as("sending правки is the «suggest» right").isEqualTo(403);

        String translate = token(translator, List.of("translate"));
        assertThat(bearer("GET", base + "/glossary", translate, null).status()).as("the glossary is the Studio's").isEqualTo(403);
        assertThat(bearer("GET", base + "/runs", translate, null).status()).as("runs of a translator with шаги only").isEqualTo(403);

        Response anonymous = new Browser(port).get("/api/v1/studio/translations");
        assertThat(anonymous.status()).isEqualTo(401);
    }

    @Test
    void theOriginalIsTheTeamsAlone() {
        String base = translation(translator);
        String studio = token(translator, List.of("studio"));
        Response pasted = bearer("POST", base + "/originals", studio, JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1", "text", "Zorian woke up at dawn " + System.nanoTime() + ".")))));
        assertThat(pasted.status()).as(pasted.body()).isEqualTo(201);
        assertThat(JSON.readTree(pasted.body()).path("saved").asInt()).isEqualTo(1);
        JsonNode list = read(bearer("GET", base + "/originals", studio, null));
        assertThat(list.path("items").path(0).path("number").asInt()).isEqualTo(1);
        assertThat(list.path("items").path(0).path("language").asString()).isEqualTo("en");
        assertThat(read(bearer("GET", base + "/originals/1", studio, null)).path("blocks").path(0).path("text").asString())
                .startsWith("Zorian woke up");

        Person stranger = Accounts.signedIn(port, mailbox);
        String theirs = token(stranger, List.of("studio", "translate"));
        Response looked = bearer("GET", base + "/originals/1", theirs, null);
        assertThat(looked.status()).as("not in the team").isEqualTo(403);
        assertThat(looked.body()).contains("Це вам недоступно");
        assertThat(bearer("GET", base + "/originals", theirs, null).status()).isEqualTo(403);
        assertThat(bearer("POST", base + "/originals", theirs, JSON.writeValueAsString(Map.of("language", "en", "text", "Other text."))).status())
                .isEqualTo(403);
        assertThat(bearer("GET", base + "/glossary", theirs, null).status()).isEqualTo(403);
        assertThat(bearer("GET", base + "/chapters", theirs, null).status()).isEqualTo(403);
        assertThat(bearer("GET", "/api/v1/studio/novels/" + "no-such-novel" + "/translations/x/chapters", theirs, null).status()).isEqualTo(404);
    }

    @Test
    void aRunIsEstimatedInShahsAndHeldFromThem() {
        String base = translation(translator);
        String other = translation(translator);
        String studio = token(translator, List.of("studio"));
        String translate = token(translator, List.of("translate"));
        paste(base, studio, 2);
        paste(other, studio, 1);

        Response hidden = bearer("GET", base + "/runs/options", translate, null);
        assertThat(hidden.status()).as("AI is hidden from those without шаги").isEqualTo(403);
        assertThat(hidden.body()).contains("лише тим, у кого є шаги");

        grant(translator, 1);
        JsonNode options = read(bearer("GET", base + "/runs/options", translate, null));
        assertThat(options.path("personal").asBoolean()).isTrue();
        assertThat(options.path("pasted").asBoolean()).isTrue();
        assertThat(options.path("sourceChapters").asInt()).isEqualTo(2);
        assertThat(options.path("balance").path("available").asInt()).isEqualTo(1);
        assertThat(options.path("presets").valueStream().map(preset -> preset.path("quality").asString()).toList())
                .containsExactly("best", "high", "draft");

        JsonNode estimate = read(bearer("POST", base + "/runs/estimate", translate, json("to", 2)));
        assertThat(estimate.path("chapters").asInt()).isEqualTo(2);
        assertThat(estimate.path("shah").asInt()).isPositive();
        int reserve = estimate.path("reserveShah").asInt();
        assertThat(reserve).isGreaterThanOrEqualTo(estimate.path("shah").asInt());
        assertThat(estimate.path("available").asInt()).isEqualTo(1);
        assertThat(estimate.path("enough").asBoolean()).isEqualTo(reserve <= 1);
        assertThat(estimate.toString()).as("costs in шаги, not dollars").doesNotContain("usd").doesNotContain("Usd");
        assertThat(bearer("POST", base + "/runs/estimate", translate, json("to", 9)).status()).as("the original goes to 2").isEqualTo(400);
        if (reserve > 1) {
            grant(translator, reserve - 1);
        }

        long run = read(bearer("POST", base + "/runs", translate, json("to", 2))).path("id").asLong();
        assertThat(read(bearer("GET", base + "/runs/options", translate, null)).path("balance").path("available").asInt())
                .as("all of it held").isZero();
        JsonNode short_ = read(bearer("POST", other + "/runs/estimate", translate, json("to", 1)));
        assertThat(short_.path("enough").asBoolean()).isFalse();
        Response refused = bearer("POST", other + "/runs", translate, json("to", 1));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("Не вистачає шагів");

        JsonNode cancelled = read(bearer("POST", base + "/runs/" + run + "/cancel", translate, "{}"));
        assertThat(cancelled.path("state").asString()).isEqualTo("cancelled");
        assertThat(read(bearer("GET", base + "/runs/options", translate, null)).path("balance").path("available").asInt())
                .as("nothing spent, everything back").isEqualTo(Math.max(1, reserve));
        assertThat(read(bearer("GET", base + "/runs", translate, null)).path("items").path(0).path("id").asLong()).isEqualTo(run);
    }

    @Test
    void fromAPastedOriginalToAPublishedChapterWithoutTheSite() {
        String base = translation(translator);
        String agent = token(translator, List.of("studio", "translate"));
        paste(base, agent, 1);
        grant(translator, 10);

        JsonNode started = read(bearer("POST", base + "/runs", agent, "{\"to\":1,\"publish\":false}"));
        long run = started.path("id").asLong();
        assertThat(started.path("personal").asBoolean()).isTrue();
        assertThat(started.path("state").asString()).isIn("queued", "running");
        worker.drain();

        JsonNode done = read(bearer("GET", base + "/runs/" + run, agent, null));
        assertThat(done.path("state").asString()).as(done.toString()).isEqualTo("done");
        assertThat(done.path("done").asInt()).isEqualTo(1);
        JsonNode log = read(bearer("GET", base + "/runs/" + run + "/log?limit=200", agent, null));
        assertThat(log.path("items").valueStream().map(event -> event.path("kind").asString()).toList()).contains("drafted");
        JsonNode firstEvents = read(bearer("GET", base + "/runs/" + run + "/log?limit=1", agent, null));
        assertThat(firstEvents.path("items")).hasSize(1);
        JsonNode nextEvents = read(bearer("GET", base + "/runs/" + run + "/log?limit=1&after=" + firstEvents.path("next").asString(), agent, null));
        assertThat(nextEvents.path("items").path(0).path("id").asLong()).isGreaterThan(firstEvents.path("items").path(0).path("id").asLong());

        JsonNode chapter = read(bearer("GET", base + "/chapters/1", agent, null));
        assertThat(chapter.path("published").asBoolean()).as("«не публікувати»: a draft first").isFalse();
        assertThat(chapter.path("draft").path("blocks")).isNotEmpty();
        assertThat(read(bearer("GET", base + "/chapters", agent, null)).path("items").path(0).path("draft").asBoolean()).isTrue();

        JsonNode published = read(bearer("POST", base + "/chapters/1/publish", agent, "{}"));
        assertThat(published.path("revision").asLong()).isPositive();
        JsonNode after = read(bearer("GET", base + "/chapters/1", agent, null));
        assertThat(after.path("published").asBoolean()).isTrue();
        assertThat(after.path("revision").asLong()).isEqualTo(published.path("revision").asLong());
        assertThat(after.path("draft").isNull() || after.path("draft").isMissingNode()).isTrue();
        assertThat(after.path("blocks")).isNotEmpty();

        assertThat(bearer("POST", base + "/chapters/1/publish", agent, "{}").status()).as("no draft left to publish").isEqualTo(400);
        long tokenId = db.select(API_TOKEN.ID).from(API_TOKEN).where(API_TOKEN.SECRET_HASH.eq(sha256(agent))).fetchSingle(API_TOKEN.ID);
        assertThat(db.select(API_TOKEN_WRITE.PATH).from(API_TOKEN_WRITE).where(API_TOKEN_WRITE.TOKEN_ID.eq(tokenId)).fetch(API_TOKEN_WRITE.PATH))
                .as("every change made with the token is journaled").contains(base + "/originals", base + "/runs", base + "/chapters/1/publish");
    }

    @Test
    void theGlossaryIsReadAddedApprovedCorrectedAndToldFacts() {
        String base = translation(translator);
        String studio = token(translator, List.of("studio"));
        paste(base, studio, 1);
        String suffix = Long.toString(System.nanoTime(), 36);
        long zorian = read(bearer("POST", base + "/glossary", studio, JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", "Зоріан",
                "original", "Zorian" + suffix, "kind", "character", "gender", "male")))).path("id").asLong();
        long town = read(bearer("POST", base + "/glossary", studio, JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", "Сіорія",
                "original", "Cyoria" + suffix, "kind", "place")))).path("id").asLong();
        read(bearer("POST", base + "/glossary", studio, JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", "Академія",
                "original", "Academy" + suffix, "kind", "organization"))));
        assertThat(bearer("POST", base + "/glossary", studio, JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", "Інший",
                "original", "Zorian" + suffix, "kind", "character"))).status()).as("the same original twice").isEqualTo(400);

        JsonNode first = read(bearer("GET", base + "/glossary?limit=2", studio, null));
        assertThat(first.path("items")).hasSize(2);
        assertThat(first.path("items").path(0).path("id").asLong()).isEqualTo(zorian);
        assertThat(first.path("items").path(0).path("original").asString()).isEqualTo("Zorian" + suffix);
        assertThat(first.path("items").path(0).path("status").asString()).isEqualTo("approved");
        JsonNode second = read(bearer("GET", base + "/glossary?limit=2&after=" + first.path("next").asString(), studio, null));
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("next").isNull()).isTrue();
        assertThat(read(bearer("GET", base + "/glossary?kind=place", studio, null)).path("items")).singleElement()
                .satisfies(entry -> assertThat(entry.path("id").asLong()).isEqualTo(town));
        assertThat(bearer("GET", base + "/glossary?status=everything", studio, null).status()).isEqualTo(400);

        JsonNode changed = read(bearer("PATCH", base + "/glossary/" + zorian, studio, json("note", "Головний герой")));
        assertThat(changed.path("note").asString()).isEqualTo("Головний герой");
        assertThat(changed.path("ukrainian").asString()).as("what was left out stays").isEqualTo("Зоріан");
        assertThat(changed.path("gender").asString()).isEqualTo("male");

        assertThat(read(bearer("POST", base + "/glossary/status", studio, "{\"ids\":[" + town + "],\"status\":\"rejected\"}"))
                .path("changed").asInt()).isEqualTo(1);
        assertThat(read(bearer("GET", base + "/glossary?status=rejected", studio, null)).path("items")).singleElement()
                .satisfies(entry -> assertThat(entry.path("id").asLong()).isEqualTo(town));
        assertThat(read(bearer("POST", base + "/glossary/status", studio, "{\"ids\":[" + town + "],\"status\":\"new\"}"))
                .path("changed").asInt()).isEqualTo(1);
        assertThat(read(bearer("POST", base + "/glossary/approve-new", studio, "{}")).path("changed").asInt()).isEqualTo(1);
        assertThat(read(bearer("GET", base + "/glossary/" + town, studio, null)).path("status").asString()).isEqualTo("approved");

        String facts = base + "/glossary/" + zorian + "/facts";
        long fact = read(bearer("POST", facts, studio, JSON.writeValueAsString(Map.of("chapter", 1, "text", "Прокидається на світанку."))))
                .path("id").asLong();
        assertThat(read(bearer("PUT", facts + "/" + fact, studio, JSON.writeValueAsString(Map.of("chapter", 1, "text", "Прокинувся на світанку."))))
                .path("text").asString()).isEqualTo("Прокинувся на світанку.");
        assertThat(read(bearer("GET", facts, studio, null))).singleElement()
                .satisfies(found -> assertThat(found.path("manual").asBoolean()).isTrue());
        assertThat(bearer("POST", facts, studio, JSON.writeValueAsString(Map.of("text", "Без глави."))).status()).isEqualTo(400);
        assertThat(bearer("DELETE", facts + "/" + fact, studio, null).status()).isEqualTo(204);
        assertThat(read(bearer("GET", facts, studio, null))).isEmpty();
    }

    @Test
    void aReaderSendsABatchOfSuggestionsAndTheTeamReviewsThem() {
        Person admin = admin();
        String base = imported(admin, "# Перша\nПерший абзац тексту.\n\nДругий абзац тексту.\n");
        String team = token(admin, List.of("studio"));
        JsonNode chapter = read(bearer("GET", base + "/chapters/1", team, null));
        String firstBlock = chapter.path("blocks").valueStream().filter(block -> block.path("type").asString().equals("paragraph"))
                .findFirst().orElseThrow().path("id").asString();

        Person reader = Accounts.signedIn(port, mailbox);
        String batch = """
                {"note":"Дрібниці","changes":[
                  {"chapter":1,"block":"%s","content":[{"text":"Перший абзац, виправлений.","marks":[]}]},
                  {"chapter":1,"find":"Другий","replacement":"Наступний"}]}""".formatted(firstBlock);
        assertThat(bearer("POST", base + "/suggestions", token(reader, List.of("studio")), batch).status())
                .as("sending is the «suggest» right").isEqualTo(403);
        String suggest = token(reader, List.of("suggest"));
        assertThat(bearer("POST", base + "/suggestions", suggest, """
                {"changes":[{"chapter":1,"find":"Немає такого","replacement":"х"}]}""").status())
                .as("checked as drafts are").isEqualTo(400);
        JsonNode sent = read(bearer("POST", base + "/suggestions", suggest, batch));
        assertThat(sent.path("sent").asInt()).isEqualTo(2);
        assertThat(bearer("GET", base + "/suggestions", token(reader, List.of("studio")), null).status())
                .as("reviewing is the team's").isEqualTo(403);

        JsonNode pending = read(bearer("GET", base + "/suggestions", team, null));
        assertThat(pending.path("items")).hasSize(2);
        JsonNode block = pending.path("items").valueStream().filter(item -> item.path("kind").asString().equals("block")).findFirst().orElseThrow();
        JsonNode replace = pending.path("items").valueStream().filter(item -> item.path("kind").asString().equals("replace")).findFirst().orElseThrow();
        assertThat(block.path("author").asString()).isEqualTo(reader.nick());
        assertThat(block.path("note").asString()).isEqualTo("Дрібниці");
        assertThat(block.path("current").path(0).path("text").asString()).isEqualTo("Перший абзац тексту.");
        assertThat(block.path("proposed").path(0).path("text").asString()).isEqualTo("Перший абзац, виправлений.");
        assertThat(replace.path("occurrences").asInt()).isEqualTo(1);
        assertThat(replace.path("stale").asBoolean()).isFalse();
        JsonNode page = read(bearer("GET", base + "/suggestions?limit=1", team, null));
        assertThat(read(bearer("GET", base + "/suggestions?limit=1&after=" + page.path("next").asString(), team, null)).path("items"))
                .hasSize(1);

        JsonNode reviewed = read(bearer("POST", base + "/suggestions/review", team, """
                {"note":"Дякую","decisions":[{"id":%d,"accept":true},{"id":%d,"accept":false}]}"""
                .formatted(block.path("id").asLong(), replace.path("id").asLong())));
        assertThat(reviewed.path("accepted").asInt()).isEqualTo(1);
        assertThat(reviewed.path("rejected").asInt()).isEqualTo(1);
        assertThat(reviewed.path("chapters").path(0).path("revision").asLong()).isPositive();
        assertThat(read(bearer("GET", base + "/chapters/1", team, null)).toString()).contains("Перший абзац, виправлений.")
                .contains("Другий абзац тексту.");
        assertThat(read(bearer("GET", base + "/suggestions", team, null)).path("items")).isEmpty();
        assertThat(bearer("POST", base + "/suggestions/review", team, """
                {"decisions":[{"id":%d,"accept":true}]}""".formatted(block.path("id").asLong())).status())
                .as("reviewed already").isEqualTo(409);
    }

    @Test
    void picturesAreUploadedWithinTheirLimitsAndPlacedIntoChapters() {
        Person admin = admin();
        String base = imported(admin, "# Перша\nПерший абзац.\n\nДругий абзац.\n");
        String studio = token(admin, List.of("studio"));

        Response big = upload(studio, "big.png", new byte[5 * 1024 * 1024 + 1]);
        assertThat(big.status()).isEqualTo(400);
        assertThat(big.body()).contains("до 5 МБ");
        Response text = upload(studio, "note.png", "не картинка".getBytes(StandardCharsets.UTF_8));
        assertThat(text.status()).isEqualTo(400);
        assertThat(text.body()).contains("не схоже на картинку");
        Response uploaded = upload(studio, "scene.png", Pictures.png(400, 300, Color.BLUE));
        assertThat(uploaded.status()).as(uploaded.body()).isEqualTo(201);
        long picture = JSON.readTree(uploaded.body()).path("id").asLong();
        assertThat(JSON.readTree(uploaded.body()).path("fileName").asString()).isEqualTo("scene.png");
        assertThat(read(bearer("GET", "/api/v1/studio/pictures", studio, null)).path("items").path(0).path("id").asLong()).isEqualTo(picture);

        assertThat(bearer("POST", base + "/pictures/preview", studio, "{\"pictures\":[{\"id\":" + picture + ",\"chapter\":1,\"after\":-1}]}")
                .status()).isEqualTo(400);
        JsonNode preview = read(bearer("POST", base + "/pictures/preview", studio, "{\"pictures\":[{\"id\":" + picture + "}]}"));
        assertThat(preview.path(0).path("problem").asString()).as("the name says nothing, no place given").contains("З назви файлу");
        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(bearer("POST", base + "/pictures", token(stranger, List.of("studio")),
                "{\"pictures\":[{\"id\":" + picture + ",\"chapter\":1,\"after\":1}]}").status()).as("not in the team").isEqualTo(403);

        JsonNode placed = read(bearer("POST", base + "/pictures", studio, "{\"pictures\":[{\"id\":" + picture + ",\"chapter\":1,\"after\":1}]}"));
        assertThat(placed.path("pictures").asInt()).isEqualTo(1);
        assertThat(placed.path("chapters").asInt()).isEqualTo(1);
        List<JsonNode> blocks = read(bearer("GET", base + "/chapters/1", studio, null)).path("blocks").valueStream().toList();
        int image = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).path("type").asString().equals("image")) {
                image = i;
            }
        }
        assertThat(image).as("after the first paragraph").isPositive();
        assertThat(blocks.get(image).path("imageId").asLong()).isEqualTo(picture);
        assertThat(blocks.get(image - 1).path("content").path(0).path("text").asString()).isEqualTo("Перший абзац.");
        assertThat(read(bearer("GET", "/api/v1/studio/pictures", studio, null)).path("items").path(0).path("uses")).isNotEmpty();
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** A new translation of the person's own, from a pasted original; its address in the API. */
    private String translation(Person person) {
        JsonNode created = read(person.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання %s","description":[],"tags":[],"language":"en"}""".formatted(UUID.randomUUID().toString().substring(0, 6))));
        String slug = created.path("novelSlug").asString();
        JsonNode mine = read(person.browser().get("/api/studio"));
        String team = mine.valueStream().filter(edition -> edition.path("novelSlug").asString().equals(slug)).findFirst().orElseThrow()
                .path("teamHandle").asString();
        return "/api/v1/studio/novels/" + slug + "/translations/" + team;
    }

    private void paste(String base, String token, int chapters) {
        long seed = System.nanoTime() + new Random().nextInt(1000);
        List<Map<String, String>> list = new java.util.ArrayList<>();
        for (int i = 1; i <= chapters; i++) {
            // Its own text: answers saved for another test's chapter would be reused for free.
            list.add(Map.of("title", "Chapter " + i, "text", "Zorian woke up at dawn " + seed + " " + i + "."));
        }
        Response pasted = bearer("POST", base + "/originals", token, JSON.writeValueAsString(Map.of("language", "en", "chapters", list)));
        assertThat(pasted.status()).as(pasted.body()).isEqualTo(201);
    }

    private Person admin() {
        Person admin = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "admin").where(ACCOUNT.NICK.eq(admin.nick())).execute();
        return admin;
    }

    /** A published novel the admin's team translates, from a file; its address in the API. */
    private String imported(Person admin, String markdown) {
        String title = "API Студії " + UUID.randomUUID().toString().substring(0, 6);
        JsonNode imported = read(admin.browser().upload("/api/admin/novels",
                Map.of("title", title, "author", "Автор", "description", "Опис.", "tags", "Фентезі", "kind", "machine", "adult", "false"),
                "file", "n.md", "text/markdown", markdown.getBytes(StandardCharsets.UTF_8)));
        String slug = imported.path("slug").asString();
        JsonNode mine = read(bearer("GET", "/api/v1/studio/translations?limit=100", token(admin, List.of("studio")), null));
        String team = mine.path("items").valueStream().filter(item -> item.path("novel").asString().equals(slug)).findFirst().orElseThrow()
                .path("team").asString();
        return "/api/v1/studio/novels/" + slug + "/translations/" + team;
    }

    private void grant(Person person, int shah) {
        assertThat(owner.browser().post("/api/admin/users/" + person.nick() + "/shahs", json("shah", shah)).status()).isEqualTo(201);
    }

    private static String token(Person person, List<String> scopes) {
        return read(person.browser().post("/api/me/api-tokens", json("name", "Агент", "scopes", scopes, "days", 7)))
                .path("secret").asString();
    }

    private Response bearer(String method, String path, String token, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, body == null || method.equals("GET") ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null && !method.equals("GET")) {
            request.header("Content-Type", "application/json");
        }
        return send(request);
    }

    private Response upload(String token, String filename, byte[] content) {
        String boundary = "----novelka" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/studio/pictures"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    private static Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.nullNode() : JSON.readTree(response.body());
    }
}
