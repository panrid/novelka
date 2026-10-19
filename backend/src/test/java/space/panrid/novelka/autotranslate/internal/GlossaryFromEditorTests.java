package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;

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
 * Етап 19, п. 4: a word selected in the translation shows the piece of the original it was
 * translated from — the original's own text, never a translation back — and goes to the
 * glossary with both parts as the team corrected them.
 */
@IntegrationTest
class GlossaryFromEditorTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    FakeModel model;

    Person owner;
    long edition;
    String blockId;

    @BeforeEach
    void theSiteOwnerPastedAChapter() {
        owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        model.reset();
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання","description":[],"tags":[]}""")).path("editionId").asLong();
        read(owner.browser().post(base() + "/originals/separate", JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1", "text", "Zorian woke up in Cirin."))))));
        blockId = read(owner.browser().get(base() + "/originals/1")).path("blocks").path(0).path("id").asString();
    }

    @Test
    void theSelectionShowsTheOriginalsOwnWordsAndGoesToTheGlossary() {
        JsonNode found = read(owner.browser().post(base() + "/chapters/1/glossary/source", question(blockId, "Зоріан")));
        assertThat(found.path("paragraph").asString()).isEqualTo("Zorian woke up in Cirin.");
        assertThat(found.path("original").asString()).isEqualTo("Zorian");
        assertThat(found.path("language").asString()).isEqualTo("en");

        JsonNode back = read(owner.browser().post(base() + "/chapters/1/glossary/source", question(blockId, "прокинувся")));
        assertThat(back.path("original").isNull()).as("words the paragraph does not have are no answer").isTrue();
        JsonNode none = read(owner.browser().post(base() + "/chapters/1/glossary/source", question("no-such-block", "Зоріан")));
        assertThat(none.path("paragraph").isNull()).isTrue();
        assertThat(owner.browser().post(base() + "/chapters/1/glossary/source", question(blockId, " ")).status()).isEqualTo(400);

        String entry = JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", " Зоріан ", "original", "Zorian", "kind", "character",
                "gender", "male"));
        assertThat(owner.browser().post(base() + "/glossary", entry).status()).isEqualTo(201);
        JsonNode added = read(owner.browser().get(base() + "/glossary?q=Зоріан")).path("items").path(0);
        assertThat(added.path("ukrainian").asString()).isEqualTo("Зоріан");
        assertThat(added.path("status").asString()).as("the team's own entry is approved").isEqualTo("approved");
        assertThat(added.path("manual").asBoolean()).isTrue();
        Response again = owner.browser().post(base() + "/glossary", entry);
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.body()).contains("уже є в словнику");
        assertThat(owner.browser().post(base() + "/glossary", JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", "Сірін",
                "original", "Cirin", "kind", "planet", "gender", "unknown"))).status()).isEqualTo(400);
    }

    @Test
    void onlyTheTeamsTranslatorsAsk() {
        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().post(base() + "/chapters/1/glossary/source", question(blockId, "Зоріан")).status()).isEqualTo(403);
        assertThat(stranger.browser().post(base() + "/glossary", JSON.writeValueAsString(Map.of("chapter", 1, "ukrainian", "Зоріан",
                "original", "Zorian", "kind", "character", "gender", "male"))).status()).isEqualTo(403);
    }

    private static String question(String block, String selection) {
        return JSON.writeValueAsString(Map.of("blockId", block, "translation", "Зоріан прокинувся в Сіріні.", "selection", selection));
    }

    private String base() {
        return "/api/studio/editions/" + edition;
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
