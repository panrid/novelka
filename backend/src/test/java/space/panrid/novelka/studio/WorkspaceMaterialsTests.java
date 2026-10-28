package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.IMAGE_FILE;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.net.http.HttpResponse;
import java.util.Map;
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
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 24.2 (issue #330): авторські матеріали: канон, плани, рішення, вкладення та історія змін у Студії.
 */
@IntegrationTest
class WorkspaceMaterialsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person author;
    Person stranger;
    Person invited;

    @BeforeEach
    void setUp() {
        author = Accounts.signedIn(port, mailbox);
        stranger = Accounts.signedIn(port, mailbox);
        invited = Accounts.signedIn(port, mailbox);
    }

    @Test
    void createReadSearchAndListWorkspaceDocuments() {
        long edition = createEdition(author, "Хроніки світу " + UUID.randomUUID().toString().substring(0, 6));
        String base = "/api/studio/editions/" + edition + "/workspace/documents";

        // 1. Create documents of different kinds
        JsonNode doc1 = read(author.browser().put(base + "/stan-roboty", json(
                "title", "Стан роботи",
                "kind", "status",
                "content", "Поточна зупинка: робота над розділом 3. Герой Лукас досліджує джерело мани.",
                "version", null
        )));
        assertThat(doc1.path("slug").asString()).isEqualTo("stan-roboty");
        assertThat(doc1.path("version").asLong()).isEqualTo(1);
        assertThat(doc1.path("kind").asString()).isEqualTo("status");

        read(author.browser().put(base + "/zakony-mahii", json(
                "title", "Закони Магії",
                "kind", "canon",
                "content", "Мана Лукаса відчувається прохолодною. Посилання на [[stan-roboty|Стан роботи]] та [[Розділ 1]].",
                "version", null
        )));

        read(author.browser().put(base + "/plan-epokhy", json(
                "title", "План епохи",
                "kind", "plan",
                "content", "Епоха 1: Пробудження та перші кроки на новому континенті.",
                "version", null
        )));

        // 2. List all documents
        JsonNode all = read(author.browser().get(base));
        assertThat(all).hasSize(3);

        // 3. Filter by kind
        JsonNode canonDocs = read(author.browser().get(base + "?kind=canon"));
        assertThat(canonDocs).singleElement().satisfies(doc -> {
            assertThat(doc.path("slug").asString()).isEqualTo("zakony-mahii");
            assertThat(doc.path("kind").asString()).isEqualTo("canon");
        });

        // 4. Search by query
        JsonNode searchResults = read(author.browser().get(base + "?q=Лукас"));
        assertThat(searchResults).extracting(doc -> doc.path("slug").asString())
                .containsExactlyInAnyOrder("stan-roboty", "zakony-mahii");
        assertThat(searchResults.get(0).path("snippet").asString()).contains("Лукас");

        // 5. Read single document with parsed links
        JsonNode single = read(author.browser().get(base + "/zakony-mahii"));
        assertThat(single.path("title").asString()).isEqualTo("Закони Магії");
        assertThat(single.path("links")).extracting(JsonNode::asString)
                .contains("stan-roboty", "Розділ 1");
    }

    @Test
    void optimisticLockingDetectsConcurrentConflict() {
        long edition = createEdition(author, "Роман з правками " + UUID.randomUUID().toString().substring(0, 6));
        String base = "/api/studio/editions/" + edition + "/workspace/documents";

        // Create version 1
        read(author.browser().put(base + "/karta-znan", json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Початковий текст карти знань.",
                "version", null
        )));

        // Author gets document version 1
        JsonNode v1 = read(author.browser().get(base + "/karta-znan"));
        long v1Num = v1.path("version").asLong();
        assertThat(v1Num).isEqualTo(1);

        // Team member or concurrent edit saves version 2
        read(author.browser().put(base + "/karta-znan", json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Оновлений текст від колеги.",
                "version", 1
        )));

        // Author tries to overwrite using old version 1 -> 409 Conflict
        Response conflict = author.browser().put(base + "/karta-znan", json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Мій паралельний текст, який не має затерти чуже.",
                "version", 1
        ));
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body()).contains("змінено іншим учасником");

        // Document in DB stays at version 2
        JsonNode v2 = read(author.browser().get(base + "/karta-znan"));
        assertThat(v2.path("version").asLong()).isEqualTo(2);
        assertThat(v2.path("content").asString()).isEqualTo("Оновлений текст від колеги.");

        // Author resolves conflict by supplying expected version 2
        Response resolved = author.browser().put(base + "/karta-znan", json(
                "title", "Карта Знань",
                "kind", "meta",
                "content", "Об'єднаний текст після розв'язання конфлікту.",
                "version", 2
        ));
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(read(author.browser().get(base + "/karta-znan")).path("version").asLong()).isEqualTo(3);
    }

    @Test
    void revisionsHistoryAndRestore() {
        long edition = createEdition(author, "Історія ревізій " + UUID.randomUUID().toString().substring(0, 6));
        String base = "/api/studio/editions/" + edition + "/workspace/documents";

        // Version 1
        read(author.browser().put(base + "/retkony", json(
                "title", "Журнал ретконів",
                "kind", "decision",
                "content", "Версія 1: початкові правила.",
                "version", null
        )));

        // Version 2
        read(author.browser().put(base + "/retkony", json(
                "title", "Журнал ретконів",
                "kind", "decision",
                "content", "Версія 2: змінено правило 42.",
                "version", 1
        )));

        // Version 3
        read(author.browser().put(base + "/retkony", json(
                "title", "Журнал ретконів",
                "kind", "decision",
                "content", "Версія 3: експериментальне правило.",
                "version", 2
        )));

        // List revisions
        JsonNode revisions = read(author.browser().get(base + "/retkony/revisions"));
        assertThat(revisions).hasSize(3);
        assertThat(revisions.get(0).path("version").asLong()).isEqualTo(3);
        assertThat(revisions.get(2).path("version").asLong()).isEqualTo(1);
        long rev1Id = revisions.get(2).path("id").asLong();

        // Restore version 1
        JsonNode restored = read(author.browser().post(base + "/retkony/revisions/" + rev1Id + "/restore?version=3", "{}"));
        assertThat(restored.path("version").asLong()).isEqualTo(4);
        assertThat(restored.path("content").asString()).isEqualTo("Версія 1: початкові правила.");
    }

    @Test
    void attachmentsAndMediaGateProtection() {
        long edition = createEdition(author, "Вкладення " + UUID.randomUUID().toString().substring(0, 6));
        author.browser().put("/api/studio/editions/" + edition + "/visibility", json("visibility", "private"));

        // Upload attachment picture
        byte[] imgBytes = Pictures.png(500, 500, Color.MAGENTA);
        long imageId = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"),
                "file", "map.png", "image/png", imgBytes)).path("id").asLong();

        // Create document
        read(author.browser().put("/api/studio/editions/" + edition + "/workspace/documents/karta-svitu", json(
                "title", "Карта Світу",
                "kind", "canon",
                "content", "Опис карти.",
                "version", null
        )));

        // Attach picture to workspace
        JsonNode attachment = read(author.browser().post("/api/studio/editions/" + edition + "/workspace/attachments", json(
                "documentSlug", "karta-svitu",
                "imageId", imageId,
                "name", "Карта півночі",
                "description", "Детальна карта гірського хребта"
        )));
        long attId = attachment.path("id").asLong();
        assertThat(attachment.path("name").asString()).isEqualTo("Карта півночі");

        // List attachments
        JsonNode list = read(author.browser().get("/api/studio/editions/" + edition + "/workspace/attachments"));
        assertThat(list).singleElement().satisfies(att -> assertThat(att.path("id").asLong()).isEqualTo(attId));

        String key = db.select(IMAGE_FILE.KEY).from(IMAGE_FILE).where(IMAGE_FILE.IMAGE_ID.eq(imageId)).fetchAny(IMAGE_FILE.KEY);
        assertThat(key).isNotNull();
        String fileUrl = "/media/" + key;

        Browser guest = new Browser(port);

        // MediaGate: Guest requesting attachment file -> 404
        assertThat(guest.fetch(fileUrl).statusCode()).isEqualTo(404);

        // MediaGate: Stranger requesting attachment file -> 404
        assertThat(stranger.browser().fetch(fileUrl).statusCode()).isEqualTo(404);

        // MediaGate: Invited reader requesting attachment file -> 404 (workspace materials are team-only!)
        author.browser().post("/api/studio/editions/" + edition + "/readers", json("nick", invited.nick()));
        assertThat(invited.browser().fetch(fileUrl).statusCode()).isEqualTo(404);

        // MediaGate: Author (team) requesting attachment file -> 200
        assertThat(author.browser().fetch(fileUrl).statusCode()).isEqualTo(200);

        // Delete attachment
        assertThat(author.browser().delete("/api/studio/editions/" + edition + "/workspace/attachments/" + attId).status())
                .isEqualTo(204);
        assertThat(read(author.browser().get("/api/studio/editions/" + edition + "/workspace/attachments"))).isEmpty();
    }

    @Test
    void strangersAndInvitedReadersCannotAccessWorkspaceMaterials() {
        long edition = createEdition(author, "Таємний простір " + UUID.randomUUID().toString().substring(0, 6));
        author.browser().put("/api/studio/editions/" + edition + "/visibility", json("visibility", "private"));
        author.browser().post("/api/studio/editions/" + edition + "/readers", json("nick", invited.nick()));

        read(author.browser().put("/api/studio/editions/" + edition + "/workspace/documents/secret", json(
                "title", "Секретний канон",
                "kind", "canon",
                "content", "Тільки для команди.",
                "version", null
        )));

        // Invited reader cannot access workspace
        assertThat(invited.browser().get("/api/studio/editions/" + edition + "/workspace/documents").status())
                .isEqualTo(404);
        assertThat(invited.browser().get("/api/studio/editions/" + edition + "/workspace/documents/secret").status())
                .isEqualTo(404);

        // Stranger cannot access workspace
        assertThat(stranger.browser().get("/api/studio/editions/" + edition + "/workspace/documents").status())
                .isEqualTo(404);
        assertThat(stranger.browser().get("/api/studio/editions/" + edition + "/workspace/documents/secret").status())
                .isEqualTo(404);

        // Guest cannot access workspace
        assertThat(new Browser(port).get("/api/studio/editions/" + edition + "/workspace/documents").status())
                .isIn(401, 404);
    }

    private long createEdition(Person person, String title) {
        return read(person.browser().post("/api/studio/editions", """
                {"kind":"original","title":"%s","description":[{"id":"d1","type":"paragraph","content":[{"text":"Опис.","marks":[]}]}],
                 "tags":["Фентезі"]}""".formatted(title))).path("editionId").asLong();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return parse(response);
    }

    private static JsonNode parse(Response response) {
        return JSON.readTree(response.body());
    }
}
