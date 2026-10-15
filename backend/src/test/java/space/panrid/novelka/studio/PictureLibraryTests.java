package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class PictureLibraryTests {

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
    void uploadedPicturesWaitInTheLibraryOfTheirOwnerOnly() {
        long first = upload(owner, "27-5.png", Color.BLUE);
        long second = upload(owner, "Сцена в лісі.png", Color.GREEN);
        owner.browser().upload("/api/media/images", Map.of("kind", "cover"), "file", "c.png", "image/png", Pictures.png(600, 900, Color.ORANGE));

        JsonNode page = read(owner.browser().get("/api/me/pictures"));
        assertThat(page.path("total").asInt()).as("a cover is not an illustration").isEqualTo(2);
        JsonNode newest = page.path("items").get(0);
        assertThat(newest.path("id").asLong()).isEqualTo(second);
        assertThat(newest.path("fileName").asString()).isEqualTo("Сцена в лісі.png");
        assertThat(newest.path("drawn").asBoolean()).isFalse();
        assertThat(newest.path("url").asString()).isNotBlank();
        assertThat(newest.path("uses")).isEmpty();
        assertThat(read(owner.browser().get("/api/me/pictures?filter=drawn")).path("total").asInt()).isZero();
        assertThat(read(owner.browser().get("/api/me/pictures?filter=unused")).path("total").asInt()).isEqualTo(2);

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(read(stranger.browser().get("/api/me/pictures")).path("total").asInt()).isZero();
        assertThat(stranger.browser().delete("/api/me/pictures/" + first).status()).isEqualTo(404);

        assertThat(owner.browser().delete("/api/me/pictures/" + first).status()).isEqualTo(204);
        JsonNode after = read(owner.browser().get("/api/me/pictures"));
        assertThat(after.path("total").asInt()).isEqualTo(1);
        assertThat(after.path("items").get(0).path("id").asLong()).isEqualTo(second);
    }

    @Test
    void picturesNamedByChapterAndParagraphGoRightAfterThatParagraph() {
        long edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Ілюстрована новела","description":[{"id":"d1","type":"paragraph","content":[{"text":"Опис.","marks":[]}]}],
                 "tags":["Фентезі"]}""")).path("editionId").asLong();
        publish(edition, 1, "Перший.", "Другий.", "Третій.");
        publish(edition, 2, "Єдиний абзац.");
        long start = upload(owner, "1-0.png", Color.RED);
        long afterSecond = upload(owner, "c001-s2.png", Color.BLUE);
        long tooFar = upload(owner, "2-5.png", Color.GREEN);
        long noChapter = upload(owner, "3-1.png", Color.YELLOW);
        long unnamed = upload(owner, "picture.png", Color.CYAN);
        String body = """
                {"editionId":%d,"imageIds":[%d,%d,%d,%d,%d]}""".formatted(edition, unnamed, noChapter, tooFar, afterSecond, start);

        JsonNode plan = read(owner.browser().post("/api/me/pictures/placement/preview", body));
        assertThat(plan.get(0).path("imageId").asLong()).isEqualTo(start);
        assertThat(plan.get(0).path("problem").isNull()).isTrue();
        assertThat(plan.get(0).path("quote").asString()).isEqualTo("на самому початку глави");
        assertThat(plan.get(1).path("imageId").asLong()).isEqualTo(afterSecond);
        assertThat(plan.get(1).path("quote").asString()).isEqualTo("Другий.");
        assertThat(problem(plan, tooFar)).isEqualTo("У главі 2 лише 1 абзаців.");
        assertThat(problem(plan, noChapter)).isEqualTo("Глави 3 немає або її ще не опубліковано.");
        assertThat(problem(plan, unnamed)).startsWith("З назви файлу не видно");

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().post("/api/me/pictures/placement", body).status()).isEqualTo(403);

        JsonNode placed = read(owner.browser().post("/api/me/pictures/placement", body));
        assertThat(placed.path("chapters").asInt()).isEqualTo(1);
        assertThat(placed.path("pictures").asInt()).isEqualTo(2);
        assertThat(placed.path("skipped")).hasSize(3);

        JsonNode blocks = read(owner.browser().get(chapter(edition, 1))).path("blocks");
        List<String> shape = new ArrayList<>();
        blocks.forEach(block -> shape.add(block.path("type").asString().equals("image")
                ? "image:" + block.path("imageId").asLong() : block.path("content").get(0).path("text").asString()));
        assertThat(shape).containsExactly("image:" + start, "Перший.", "Другий.", "image:" + afterSecond, "Третій.");

        JsonNode library = read(owner.browser().get("/api/me/pictures"));
        library.path("items").forEach(item -> {
            if (item.path("id").asLong() == start) {
                assertThat(item.path("uses").get(0).path("number").asInt()).isEqualTo(1);
                assertThat(item.path("uses").get(0).path("title").asString()).isEqualTo("Ілюстрована новела");
            }
        });
        assertThat(read(owner.browser().get("/api/me/pictures?filter=unused")).path("total").asInt()).isEqualTo(3);
        assertThat(problem(read(owner.browser().post("/api/me/pictures/placement/preview", body)), start))
                .isEqualTo("Уже стоїть у цій главі.");

        assertThat(owner.browser().delete("/api/me/pictures/" + start).status()).isEqualTo(204);
        assertThat(read(owner.browser().get(chapter(edition, 1))).path("blocks").get(0).path("imageId").asLong())
                .as("putting a picture away keeps it in the chapter").isEqualTo(start);
    }

    private void publish(long edition, int number, String... paragraphs) {
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        StringBuilder blocks = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            blocks.append(i == 0 ? "" : ",").append("""
                    {"id":"b%d","type":"paragraph","content":[{"text":"%s","marks":[]}]}""".formatted(i + 1, paragraphs[i]));
        }
        read(owner.browser().post(chapter(edition, number) + "/publish", """
                {"title":"Глава %d","baseRevisionId":null,"blocks":[%s]}""".formatted(number, blocks)));
    }

    private static String problem(JsonNode plan, long imageId) {
        for (JsonNode place : plan) {
            if (place.path("imageId").asLong() == imageId) {
                return place.path("problem").asString();
            }
        }
        throw new AssertionError("no place for " + imageId);
    }

    private static long upload(Person person, String name, Color color) {
        return read(person.browser().upload("/api/media/images", "file", name, "image/png", Pictures.png(800, 600, color)))
                .path("id").asLong();
    }

    private static String chapter(long edition, int number) {
        return "/api/studio/editions/" + edition + "/chapters/" + number;
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
