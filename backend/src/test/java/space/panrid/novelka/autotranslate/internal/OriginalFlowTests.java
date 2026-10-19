package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.nio.charset.StandardCharsets;
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

/** Етап 19: the original a translator pastes or uploads, split into chapters, for the team only. */
@IntegrationTest
class OriginalFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TEXT = """
            第1話　灯台守
            灯台の火が消えた夜。
            第2話　朝
            朝が来た。
            """;

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person owner;
    long edition;

    @BeforeEach
    void theSiteOwnerTranslatesANovelByHand() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Доглядач маяка","description":[],"tags":[]}""")).path("editionId").asLong();
    }

    @Test
    void pastedTextIsSplitShownAndKeptWithItsLanguage() {
        JsonNode preview = read(form(owner, "/preview", Map.of("text", TEXT)));
        assertThat(preview.path("firstNumber").asInt()).isEqualTo(1);
        assertThat(preview.path("chapters").size()).isEqualTo(2);
        assertThat(preview.path("chapters").path(1).path("title").asString()).isEqualTo("第2話　朝");
        assertThat(preview.path("chapters").path(1).path("start").asString()).isEqualTo("朝が来た。");
        assertThat(read(owner.browser().get(base())).path("total").asInt()).as("a preview saves nothing").isZero();

        JsonNode saved = read(form(owner, "", Map.of("text", TEXT, "language", "en")));
        assertThat(saved.path("from").asInt()).isEqualTo(1);
        assertThat(saved.path("to").asInt()).isEqualTo(2);

        // The English source ran out: the next chapters come from the Japanese one.
        read(form(owner, "", Map.of("text", "Chapter 3\nHe came back.", "language", "ja")));
        JsonNode list = read(owner.browser().get(base()));
        assertThat(list.path("total").asInt()).isEqualTo(3);
        assertThat(list.path("nextNumber").asInt()).isEqualTo(4);
        assertThat(list.path("languages").path("en").asInt()).isEqualTo(2);
        assertThat(list.path("items").path(2).path("language").asString()).isEqualTo("ja");
        read(owner.browser().patch(base() + "/language", "{\"from\":1,\"to\":2,\"language\":\"ja\"}"));
        assertThat(read(owner.browser().get(base())).path("languages").path("ja").asInt()).isEqualTo(3);

        // Pasting again over a number replaces that chapter's original.
        JsonNode again = read(form(owner, "/preview", Map.of("text", "第2話\n別の朝。", "first", "2")));
        assertThat(again.path("chapters").path(0).path("replaces").asBoolean()).isTrue();
        read(form(owner, "", Map.of("text", "第2話\n別の朝。", "first", "2", "language", "ja")));
        assertThat(read(owner.browser().get(base() + "/2")).path("blocks").path(0).path("content").path(0).path("text").asString())
                .isEqualTo("別の朝。");

        read(owner.browser().delete(base() + "/3"));
        assertThat(read(owner.browser().get(base())).path("total").asInt()).isEqualTo(2);
        read(owner.browser().delete(base()));
        assertThat(read(owner.browser().get(base())).path("total").asInt()).as("«Видалити оригінали»").isZero();
    }

    @Test
    void aTxtFileIsTakenAndBadInputIsExplained() {
        byte[] file = "Chapter 1\nThe light went out.\nChapter 2\nMorning came.".getBytes(StandardCharsets.UTF_8);
        JsonNode saved = read(owner.browser().upload(base(), Map.of("language", "en"), "file", "novel.txt", "text/plain", file));
        assertThat(saved.path("saved").asInt()).isEqualTo(2);

        assertThat(form(owner, "", Map.of("text", TEXT, "language", "fr")).status()).as("languages the site offers").isEqualTo(400);
        assertThat(form(owner, "/preview", Map.of("text", "  ")).status()).isEqualTo(400);
        assertThat(owner.browser().upload(base() + "/preview", Map.of(), "file", "book.pdf", "application/pdf", new byte[] {1}).status())
                .isEqualTo(400);
    }

    @Test
    void onlyTheTeamSeesIt() {
        read(form(owner, "", Map.of("text", TEXT, "language", "ja")));
        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().get(base()).status()).isEqualTo(403);
        assertThat(new Browser(port).get(base()).status()).isEqualTo(401);

        long own = read(owner.browser().post("/api/studio/editions", """
                {"kind":"original","title":"Мій твір","description":[],"tags":[]}""")).path("editionId").asLong();
        assertThat(owner.browser().get("/api/studio/editions/" + own + "/originals").status()).as("an own work has no original")
                .isEqualTo(400);
    }

    @Test
    void theEditorShowsThePastedOriginalBesideTheTranslation() {
        read(form(owner, "", Map.of("text", TEXT, "language", "ja")));
        read(owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}"));
        JsonNode editor = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/1"));
        assertThat(editor.path("hasOriginal").asBoolean()).isTrue();
        JsonNode original = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/1/original"));
        assertThat(original.path("blocks").path(0).path("text").asString()).isEqualTo("灯台の火が消えた夜。");
    }

    private String base() {
        return "/api/studio/editions/" + edition + "/originals";
    }

    /** A form as the page sends it for pasted text: fields only (the helper adds an empty, unnamed part). */
    private Response form(Person person, String path, Map<String, String> fields) {
        return person.browser().upload(base() + path, fields, "blank", "blank.bin", "application/octet-stream", new byte[0]);
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
