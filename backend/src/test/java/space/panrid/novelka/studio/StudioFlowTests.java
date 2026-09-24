package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class StudioFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    Person owner;

    @BeforeEach
    void signIn() {
        owner = Accounts.signedIn(port, mailbox);
    }

    @Test
    void fromANewPublicationToAPublishedChapter() {
        long edition = createPublication(owner, "Сто ночей у крамниці ліхтарів", "human");
        assertThat(read(owner.browser().get("/api/studio"))).anySatisfy(item ->
                assertThat(item.path("editionId").asLong()).isEqualTo(edition));
        String slug = read(owner.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
        assertThat(new Browser(port).get("/api/novels/" + slug).status()).as("nothing to read yet").isEqualTo(404);

        int number = read(owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}")).path("number").asInt();
        assertThat(number).isEqualTo(1);

        owner.browser().put(chapter(edition, number) + "/draft", text("Ніч перша", null, "Недописаний абзац"));
        JsonNode editor = read(owner.browser().get(chapter(edition, number)));
        assertThat(editor.path("published").asBoolean()).isFalse();
        assertThat(editor.path("draft").path("title").asString()).isEqualTo("Ніч перша");

        JsonNode published = read(owner.browser().post(chapter(edition, number) + "/publish",
                text("Ніч перша", null, "Ліхтарі спалахнули одночасно.")));
        long revision = published.path("revisionId").asLong();

        JsonNode reader = read(new Browser(port).get("/api/novels/" + slug + "/chapters/1"));
        assertThat(reader.path("title").asString()).isEqualTo("Ніч перша");
        assertThat(reader.path("blocks").get(0).path("content").get(0).path("text").asString()).isEqualTo("Ліхтарі спалахнули одночасно.");
        assertThat(read(owner.browser().get(chapter(edition, number))).path("draft").isNull())
                .as("publishing clears my draft").isTrue();

        owner.browser().post(chapter(edition, number) + "/publish", text("Ніч перша", revision, "Ліхтарі спалахнули разом."));
        JsonNode history = read(owner.browser().get(chapter(edition, number) + "/revisions"));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).path("published").asBoolean()).isTrue();
        assertThat(history.get(0).path("authorNick").asString()).isEqualTo(owner.nick());
        JsonNode diff = read(owner.browser().get(chapter(edition, number) + "/revisions/" + history.get(0).path("id").asLong()));
        assertThat(diff.path("parentBlocks").get(0).path("content").get(0).path("text").asString()).isEqualTo("Ліхтарі спалахнули одночасно.");
        assertThat(read(owner.browser().get("/api/studio/editions/" + edition + "/contributions")))
                .singleElement().satisfies(row -> assertThat(row.path("revisions").asInt()).isEqualTo(2));
    }

    @Test
    void aColleaguesNewerVersionIsNeverOverwrittenSilently() {
        long edition = createPublication(owner, "Спільна глава", "human");
        String handle = read(owner.browser().get("/api/studio/editions/" + edition)).path("teamHandle").asString();
        Person editor = Accounts.signedIn(port, mailbox);
        assertThat(owner.browser().post("/api/teams/" + handle + "/members", json("nick", editor.nick(), "role", "editor"))
                .status()).isEqualTo(204);
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        long first = read(owner.browser().post(chapter(edition, 1) + "/publish", text("Глава", null, "Перший варіант."))).path("revisionId").asLong();

        long seenByEditor = read(editor.browser().get(chapter(edition, 1))).path("revisionId").asLong();
        assertThat(seenByEditor).isEqualTo(first);
        owner.browser().post(chapter(edition, 1) + "/publish", text("Глава", first, "Другий варіант власника."));

        Response late = editor.browser().post(chapter(edition, 1) + "/publish", text("Глава", seenByEditor, "Правка редактора."));
        assertThat(late.status()).isEqualTo(409);
        assertThat(parse(late).path("reason").asString()).isEqualTo("chapter-changed");
        assertThat(read(editor.browser().get(chapter(edition, 1))).path("draft").path("blocks").get(0).path("content").get(0)
                .path("text").asString()).as("the editor's text is kept as a draft").isEqualTo("Правка редактора.");
    }

    @Test
    void editorsEditTextButDoNotAddChaptersOrPictures() {
        long edition = createPublication(owner, "Ролі в команді", "human");
        String handle = read(owner.browser().get("/api/studio/editions/" + edition)).path("teamHandle").asString();
        Person editor = Accounts.signedIn(port, mailbox);
        owner.browser().post("/api/teams/" + handle + "/members", json("nick", editor.nick(), "role", "editor"));
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        long revision = read(owner.browser().post(chapter(edition, 1) + "/publish", text("Глава", null, "Текст."))).path("revisionId").asLong();

        assertThat(editor.browser().post("/api/studio/editions/" + edition + "/chapters", "{}").status()).isEqualTo(403);
        assertThat(editor.browser().post(chapter(edition, 1) + "/publish", text("Глава", revision, "Виправлений текст.")).status())
                .isEqualTo(200);
        assertThat(editor.browser().patch("/api/studio/editions/" + edition, json("title", "Інша назва")).status()).isEqualTo(403);

        long picture = read(editor.browser().upload("/api/media/images", "file", "a.png", "image/png",
                Pictures.png(800, 600, Color.BLUE))).path("id").asLong();
        long current = read(editor.browser().get(chapter(edition, 1))).path("revisionId").asLong();
        Response withPicture = editor.browser().post(chapter(edition, 1) + "/publish", """
                {"title":"Глава","baseRevisionId":%d,"blocks":[
                  {"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]},
                  {"id":"b2","type":"image","imageId":%d}]}""".formatted(current, picture));
        assertThat(withPicture.status()).isEqualTo(403);
        assertThat(parse(withPicture).path("detail").asString()).isEqualTo("Додавати картинки можуть лише власник і перекладачі.");

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().get(chapter(edition, 1)).status()).isEqualTo(403);
    }

    @Test
    void teamsHaveNamesHandlesMembersAndAPublicPage() {
        String handle = "kitsune_" + owner.nick().substring(5, 9);
        Response created = owner.browser().post("/api/teams", json("name", "Кіцуне " + handle, "handle", handle));
        assertThat(created.status()).isEqualTo(201);
        Person member = Accounts.signedIn(port, mailbox);
        owner.browser().post("/api/teams/" + handle + "/members", json("nick", member.nick(), "role", "translator"));

        assertThat(read(member.browser().get("/api/me/teams"))).anySatisfy(team -> {
            assertThat(team.path("handle").asString()).isEqualTo(handle);
            assertThat(team.path("role").asString()).isEqualTo("translator");
        });
        assertThat(member.browser().post("/api/teams/" + handle + "/members", json("nick", owner.nick(), "role", "editor"))
                .status()).as("only the owner manages members").isEqualTo(403);

        long edition = read(member.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Переклад команди","team":"%s","tags":["Фентезі"]}""".formatted(handle))).path("editionId").asLong();
        member.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        member.browser().post(chapter(edition, 1) + "/publish", text("Перша", null, "Текст."));

        JsonNode page = read(new Browser(port).get("/api/teams/" + handle.toUpperCase()));
        assertThat(page.path("name").asString()).isEqualTo("Кіцуне " + handle);
        assertThat(page.path("members")).extracting(p -> p.path("role").asString()).containsExactly("owner", "translator");
        assertThat(page.path("editions")).singleElement().satisfies(e -> assertThat(e.path("title").asString()).isEqualTo("Переклад команди"));

        assertThat(owner.browser().post("/api/teams", json("name", "кіцуне " + handle.toUpperCase(), "handle", handle + "x")).status())
                .as("team names are unique regardless of case").isEqualTo(409);
        assertThat(member.browser().delete("/api/teams/" + handle + "/members/" + member.nick()).status()).isEqualTo(204);
        assertThat(member.browser().get("/api/studio/editions/" + edition).status()).isEqualTo(403);
    }

    @Test
    void chaptersComeFromAFileAndNumberingContinues() {
        long edition = createPublication(owner, "З файлу", "original");
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        owner.browser().post(chapter(edition, 1) + "/publish", text("Вступ", null, "Текст."));
        byte[] file = "# Друга\nАбзац.\n# Третя\n**Жирний** абзац.\n## підзаголовок\n".getBytes(StandardCharsets.UTF_8);

        JsonNode preview = read(owner.browser().upload("/api/studio/editions/" + edition + "/import/preview", "file", "book.md", "text/markdown", file));
        assertThat(preview.path("firstNumber").asInt()).isEqualTo(2);
        assertThat(preview.path("chapters")).extracting(c -> c.path("title").asString()).containsExactly("Друга", "Третя");
        assertThat(preview.path("simplified").get(0).asString()).contains("Підзаголовки");

        JsonNode imported = read(owner.browser().upload("/api/studio/editions/" + edition + "/import", "file", "book.md", "text/markdown", file));
        assertThat(imported.path("numbers")).extracting(JsonNode::asInt).containsExactly(2, 3);
        JsonNode overview = read(owner.browser().get("/api/studio/editions/" + edition));
        assertThat(overview.path("chapterCount").asInt()).isEqualTo(3);
        assertThat(overview.path("author").asString()).as("an original work is signed by its author").isEqualTo(owner.nick());
    }

    @Test
    void theOwnerChangesDataAndCover() {
        long edition = createPublication(owner, "Стара назва", "human");

        JsonNode updated = read(owner.browser().patch("/api/studio/editions/" + edition,
                json("title", "Нова назва", "status", "completed", "tags", List.of("Затишне"))));
        assertThat(updated.path("title").asString()).isEqualTo("Нова назва");
        assertThat(updated.path("status").asString()).isEqualTo("completed");
        assertThat(updated.path("tags")).extracting(JsonNode::asString).containsExactly("Затишне");

        long cover = read(owner.browser().upload("/api/media/images", Map.of("kind", "cover"), "file", "c.png", "image/png",
                Pictures.png(600, 900, Color.ORANGE))).path("id").asLong();
        assertThat(read(owner.browser().put("/api/studio/editions/" + edition + "/cover", json("imageId", cover)))
                .path("coverUrl").asString()).startsWith("/media/").endsWith("-480.jpg");
    }

    @Test
    void picturesByLinkNeverReachInternalAddresses() {
        for (String url : List.of("http://example.com/a.png", "https://127.0.0.1/a.png", "https://localhost/a.png",
                "https://10.0.0.5/a.png", "https://[::1]/a.png")) {
            Response response = owner.browser().post("/api/media/images/from-url", json("url", url));
            assertThat(response.status()).as(url).isEqualTo(400);
        }
    }

    private long createPublication(Person person, String title, String kind) {
        return read(person.browser().post("/api/studio/editions", """
                {"kind":"%s","title":"%s","description":[{"id":"d1","type":"paragraph","content":[{"text":"Опис.","marks":[]}]}],
                 "tags":["Фентезі"]}""".formatted(kind, title))).path("editionId").asLong();
    }

    private static String chapter(long edition, int number) {
        return "/api/studio/editions/" + edition + "/chapters/" + number;
    }

    private static String text(String title, Long base, String paragraph) {
        return """
                {"title":"%s","baseRevisionId":%s,"blocks":[{"id":"b1","type":"paragraph","content":[{"text":"%s","marks":[]}]}]}"""
                .formatted(title, base == null ? "null" : base.toString(), paragraph);
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return parse(response);
    }

    private static JsonNode parse(Response response) {
        return JSON.readTree(response.body());
    }
}
