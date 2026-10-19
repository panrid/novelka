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
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 20, історія глави: every version says who made it and how — the team, AI with its stage,
 * models and run, readers' правки with who proposed and who took them — and an old one comes back
 * as a new version.
 */
@IntegrationTest
class ChapterHistoryTests {

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
    long edition;

    @BeforeEach
    void aTranslatedPastedChapter() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        model.reset();
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[]}""")).path("editionId").asLong();
        read(owner.browser().post(base() + "/originals/separate", JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1", "text", "The bell rang " + System.nanoTime() + " times."))))));
        read(owner.browser().post(base() + "/autotranslate/jobs", json("to", 1)));
        worker.drain();
    }

    @Test
    void everyVersionSaysWhoMadeItAndAnOldOneComesBack() {
        String chapter = base() + "/chapters/1";
        JsonNode first = read(owner.browser().get(chapter + "/revisions")).path(0);
        assertThat(first.path("origin").asString()).isEqualTo("ai");
        assertThat(first.path("stage").asString()).isEqualTo("translate");
        assertThat(first.path("model").asString()).contains("openai/gpt-4.1-mini");
        assertThat(first.path("jobId").asLong()).isPositive();
        long machine = first.path("id").asLong();

        assertThat(read(owner.browser().post(chapter + "/check", "{}")).path("proposed").asInt()).isPositive();
        JsonNode proposed = read(owner.browser().get(chapter + "/suggestions"));
        read(owner.browser().post(chapter + "/suggestions/review", JSON.writeValueAsString(Map.of("decisions",
                proposed.valueStream().map(item -> Map.of("id", item.path("id").asLong(), "accept", true)).toList()))));
        JsonNode taken = read(owner.browser().get(chapter + "/revisions")).path(0);
        assertThat(taken.path("origin").asString()).isEqualTo("suggestion");
        assertThat(taken.path("authorNick").asString()).as("who took them").isEqualTo(owner.nick());
        assertThat(taken.path("proposers").valueStream().map(JsonNode::asString).toList()).containsExactly(owner.nick());
        assertThat(taken.path("notes").valueStream().map(JsonNode::asString).toList()).containsExactly("Вичитка ШІ");

        assertThat(owner.browser().post(chapter + "/revisions/" + taken.path("id").asLong() + "/restore", "{}").status())
                .as("the version readers see already").isEqualTo(400);
        assertThat(owner.browser().post(chapter + "/revisions/" + machine + "/restore", "{}").status()).isBetween(200, 201);
        JsonNode restored = read(owner.browser().get(chapter + "/revisions"));
        assertThat(restored.path(0).path("origin").asString()).isEqualTo("editor");
        assertThat(restored.path(0).path("published").asBoolean()).isTrue();
        assertThat(restored).as("the later version stays in the history").hasSize(3);
        JsonNode text = read(owner.browser().get(chapter + "/revisions/" + restored.path(0).path("id").asLong()));
        JsonNode original = read(owner.browser().get(chapter + "/revisions/" + machine));
        assertThat(text.path("blocks")).isEqualTo(original.path("blocks"));

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().post(chapter + "/revisions/" + machine + "/restore", "{}").status()).isEqualTo(403);
    }

    private String base() {
        return "/api/studio/editions/" + edition;
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
