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
 * Етап 19, п. 5: AI in the editor — other versions of a paragraph to pick from, and «Перевірити
 * главу», whose changes wait as suggestions the team accepts one by one.
 */
@IntegrationTest
class EditorAiTests {

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
    void theSiteOwnerTranslatedAPastedChapter() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        model.reset();
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.STATE.in("queued", "running", "failed")).execute();
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[]}""")).path("editionId").asLong();
        read(owner.browser().post(base() + "/originals/separate", JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1", "text", "The bell rang twice.\nNobody came."))))));
        read(owner.browser().post(base() + "/autotranslate/jobs", json("to", 1)));
        worker.drain();
    }

    @Test
    void otherVersionsOfAParagraph() {
        String block = read(owner.browser().get(base() + "/originals/1")).path("blocks").path(0).path("id").asString();
        JsonNode variants = read(owner.browser().post(base() + "/chapters/1/variants", json("blockId", block, "text", "Дзвін задзвонив двічі.")))
                .path("variants");
        assertThat(variants.valueStream().map(JsonNode::asString).toList()).containsExactly("Перший варіант.", "Другий варіант.", "Третій варіант.");
        String asked = model.requests.getLast().path("messages").path(1).path("content").asString();
        assertThat(asked).as("the paragraph's original goes along").contains("The bell rang twice.");
        assertThat(owner.browser().post(base() + "/chapters/1/variants", json("blockId", block, "text", " ")).status()).isEqualTo(400);
    }

    @Test
    void theChapterCheckWaitsAsSuggestionsAcceptedOneByOne() {
        JsonNode checked = read(owner.browser().post(base() + "/chapters/1/check", "{}"));
        assertThat(checked.path("proposed").asInt()).isEqualTo(2);
        JsonNode items = read(owner.browser().get(base() + "/chapters/1/suggestions"));
        assertThat(items).hasSize(2).allSatisfy(item -> assertThat(item.path("note").asString()).isEqualTo(EditorAi.NOTE));
        long first = items.path(0).path("id").asLong();
        long second = items.path(1).path("id").asLong();
        JsonNode reviewed = read(owner.browser().post(base() + "/chapters/1/suggestions/review", JSON.writeValueAsString(Map.of(
                "decisions", List.of(Map.of("id", first, "accept", true), Map.of("id", second, "accept", false))))));
        assertThat(reviewed.path("accepted").asInt()).isEqualTo(1);
        assertThat(reviewed.path("rejected").asInt()).isEqualTo(1);

        assertThat(owner.browser().post(base() + "/chapters/9/check", "{}").status()).as("no such chapter").isEqualTo(404);
        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().post(base() + "/chapters/1/check", "{}").status()).isEqualTo(403);
    }

    private String base() {
        return "/api/studio/editions/" + edition;
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
