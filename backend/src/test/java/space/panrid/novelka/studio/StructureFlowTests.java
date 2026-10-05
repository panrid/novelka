package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Volumes, a prologue and side stories put on a translation that already has chapters (етап 15). */
@IntegrationTest
class StructureFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Test
    void aTranslationIsSplitIntoVolumesAndReadersSeeTheNewNumbers() {
        Person translator = Accounts.signedIn(port, mailbox);
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Переклад з томами %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        for (int number = 1; number <= 6; number++) {
            translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
            read(translator.browser().post("/api/studio/editions/" + edition + "/chapters/" + number + "/publish", """
                    {"title":"Назва %d","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""".formatted(number)));
        }
        String base = "/api/studio/editions/" + edition + "/structure";
        String change = """
                {"numbering":"continuous","volumes":[
                  {"firstNumber":1,"title":"Пролог","kind":"prologue"},
                  {"firstNumber":2,"title":"Повільне життя","kind":"volume"},
                  {"firstNumber":4,"title":"Інтерлюдії","kind":"side"},
                  {"firstNumber":5,"title":"Подорож удвох","kind":"volume"}],
                 "automatic":[],"unnumbered":[]}""";

        JsonNode preview = read(translator.browser().post(base + "/preview", change));
        assertThat(preview.path("1").asString()).isEmpty();
        assertThat(preview.path("2").asString()).isEqualTo("1");
        assertThat(preview.path("5").asString()).isEqualTo("3");
        assertThat(read(translator.browser().get(base)).path("volumes").size()).as("a preview saves nothing").isZero();

        JsonNode saved = read(translator.browser().put(base, change));
        assertThat(saved.path("volumes").size()).isEqualTo(4);
        assertThat(saved.path("chapters").findValuesAsString("label")).containsExactly("", "1", "2", "", "3", "4");

        // Readers: chapters grouped by volumes, the chapter knows its volume, addresses stay.
        String slug = read(translator.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        Browser reader = new Browser(port);
        JsonNode list = read(reader.get("/api/novels/" + slug + "/chapters")).path("items");
        assertThat(list.path(0).path("volume").path("kind").asString()).isEqualTo("prologue");
        assertThat(list.path(4).path("volume").path("title").asString()).isEqualTo("Подорож удвох");
        JsonNode fifth = read(reader.get("/api/novels/" + slug + "/chapters/5"));
        assertThat(fifth.path("label").asString()).isEqualTo("3");
        assertThat(fifth.path("volume").path("title").asString()).isEqualTo("Подорож удвох");

        // A new chapter at the end gets the next number by itself.
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        assertThat(read(translator.browser().get(base)).path("chapters").path(6).path("label").asString()).isEqualTo("5");

        // Volumes may not start at one chapter twice; strangers may not change them.
        assertThat(translator.browser().put(base, """
                {"numbering":"continuous","volumes":[{"firstNumber":2,"title":"","kind":"volume"},{"firstNumber":2,"title":"","kind":"side"}]}""")
                .status()).isEqualTo(400);
        assertThat(Accounts.signedIn(port, mailbox).browser().put(base, change).status()).isEqualTo(403);
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
