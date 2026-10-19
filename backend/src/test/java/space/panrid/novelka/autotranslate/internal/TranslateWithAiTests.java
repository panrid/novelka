package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
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
import space.panrid.novelka.support.Eventually;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Рішення власника 2026-10-18 (етап 19): «Перекласти з ШІ» from a pasted original is for everyone
 * who has шаги; «Автопереклад із сайту» only with the site owner's leave, asked for with
 * «Попросити доступ» and answered in «Користувачі й ролі».
 */
@IntegrationTest
class TranslateWithAiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @Test
    void anyoneWithShahsTranslatesWhatTheyPastedButNotFromTheOriginalsSite() {
        long edition = read(reader.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[]}""")).path("editionId").asLong();
        String originals = "/api/studio/editions/" + edition + "/originals";
        String base = "/api/studio/editions/" + edition + "/autotranslate";
        read(reader.browser().post(originals + "/separate", JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1",
                        // Its own text: answers saved for another test's chapter would be reused for free.
                        "text", "Zorian woke up at dawn " + System.nanoTime() + "."))))));

        Response hidden = reader.browser().get(base);
        assertThat(hidden.status()).as("pasting is for everyone, AI is not for those without шаги").isEqualTo(403);
        assertThat(hidden.body()).contains("лише тим, у кого є шаги");

        assertThat(owner.browser().post("/api/admin/users/" + reader.nick() + "/shahs", json("shah", 10)).status()).isEqualTo(201);
        assertThat(read(reader.browser().get("/api/me")).path("autotranslate").asBoolean())
                .as("шаги open «Перекласти з ШІ», not the original's site").isFalse();
        JsonNode overview = read(reader.browser().get(base));
        assertThat(overview.path("pasted").asBoolean()).isTrue();
        assertThat(overview.path("personal").asBoolean()).as("paid with their own шаги").isTrue();
        assertThat(reader.browser().post(base + "/jobs", json("to", 1)).status()).isEqualTo(201);
        worker.drain();
        JsonNode job = read(reader.browser().get(base)).path("jobs").path(0);
        assertThat(job.path("state").asString()).as(job.toString()).isEqualTo("done");
        assertThat(read(reader.browser().get("/api/me/shahs")).path("available").asInt()).isLessThan(10);

        Response site = reader.browser().post("/api/studio/autotranslate/prepare", json("url", "https://ncode.syosetu.com/n0001aa/"));
        assertThat(site.status()).isEqualTo(403);
        assertThat(site.body()).contains("Попросіть доступ");
        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().get(base).status()).as("still only the team").isEqualTo(403);
    }

    @Test
    void thePersonAsksTheOwnerAnswersAndBothHearOfIt() {
        String request = "/api/autotranslate/request";
        assertThat(owner.browser().post(request, "{}").status()).as("the owner has it always").isEqualTo(400);
        assertThat(reader.browser().post(request, "{}").status()).isEqualTo(204);
        assertThat(reader.browser().post(request, "{}").status()).as("asking again changes nothing").isEqualTo(204);
        assertThat(read(reader.browser().get("/api/me")).path("autotranslateRequested").asBoolean()).isTrue();
        assertThat(reader.browser().get("/api/admin/autotranslate-requests").status()).isEqualTo(403);
        assertThat(nicks(read(owner.browser().get("/api/admin/autotranslate-requests")))).contains(reader.nick());
        JsonNode asked = Eventually.eventually(() -> read(owner.browser().get("/api/notifications")),
                page -> page.toString().contains(reader.nick())).path("items");
        assertThat(asked).filteredOn(item -> item.path("kind").asString().equals("autotranslate_request")
                && item.path("payload").path("actorNick").asString().equals(reader.nick())).hasSize(1);

        assertThat(owner.browser().post("/api/admin/users/" + reader.nick() + "/autotranslate/refuse", "{}").status()).isBetween(200, 204);
        assertThat(nicks(read(owner.browser().get("/api/admin/autotranslate-requests")))).doesNotContain(reader.nick());
        assertThat(read(reader.browser().get("/api/me")).path("autotranslateRequested").asBoolean()).isFalse();
        JsonNode refused = Eventually.eventually(() -> read(reader.browser().get("/api/notifications")),
                page -> page.toString().contains("autotranslate_answered")).path("items");
        assertThat(refused).anySatisfy(item -> assertThat(item.path("payload").path("granted").asBoolean()).isFalse());
        assertThat(read(owner.browser().get("/api/admin/audit")).path("items").path(0).path("action").asString())
                .isEqualTo("autotranslate_refused");

        assertThat(reader.browser().post(request, "{}").status()).as("they may ask again").isEqualTo(204);
        assertThat(owner.browser().put("/api/admin/users/" + reader.nick() + "/autotranslate", json("allowed", true)).status())
                .isBetween(200, 204);
        JsonNode me = read(reader.browser().get("/api/me"));
        assertThat(me.path("autotranslate").asBoolean()).isTrue();
        assertThat(me.path("autotranslateRequested").asBoolean()).isFalse();
        assertThat(nicks(read(owner.browser().get("/api/admin/autotranslate-requests")))).doesNotContain(reader.nick());
        JsonNode granted = Eventually.eventually(() -> read(reader.browser().get("/api/notifications")),
                page -> page.toString().contains("\"granted\":true")).path("items");
        assertThat(granted).anySatisfy(item -> assertThat(item.path("kind").asString()).isEqualTo("autotranslate_answered"));
        assertThat(reader.browser().post(request, "{}").status()).as("already open").isEqualTo(400);
    }

    private static List<String> nicks(JsonNode requests) {
        return requests.valueStream().map(request -> request.path("nick").asString()).toList();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.nullNode() : JSON.readTree(response.body());
    }
}
