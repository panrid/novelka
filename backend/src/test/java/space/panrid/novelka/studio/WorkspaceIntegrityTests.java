package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;
import static space.panrid.novelka.jooq.Tables.*;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipInputStream;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.access.PublicationNotices;
import space.panrid.novelka.studio.internal.WorkspaceService;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class WorkspaceIntegrityTests {
    static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort int port;
    @Autowired TestMailbox mailbox;
    @Autowired DSLContext db;
    @Autowired WorkspaceService workspace;
    @Autowired PublicationNotices notices;
    @org.springframework.beans.factory.annotation.Value("${novelka.media.dir}") java.nio.file.Path mediaDirectory;

    @Test
    void workingFilesRemainPrivateInPublicWorksAfterRemovalAndCannotBeClaimedByAnotherTeam() {
        var author = Accounts.signedIn(port, mailbox);
        var other = Accounts.signedIn(port, mailbox);
        long edition = edition(author);
        long otherEdition = edition(other);
        assertThat(author.browser().put("/api/studio/editions/" + edition + "/visibility", json("visibility", "public")).status()).isEqualTo(200);
        byte[] bytes = Pictures.png(500, 500, Color.CYAN);
        JsonNode upload = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"), "file", "map.png", "image/png", bytes));
        String url = upload.path("url").asString();
        assertThat(new Browser(port).download(url).status()).isEqualTo(404); // upload-to-attach gap
        long id = upload.path("id").asLong();
        JsonNode attachment = read(author.browser().post(base(edition) + "/attachments", json("imageId", id, "name", "map.png")));
        assertThat(new Browser(port).download(url).status()).isEqualTo(404); // public work, private note
        assertThat(other.browser().post(base(otherEdition) + "/attachments", json("imageId", id, "name", "claimed.png")).status()).isBetween(400, 499);
        assertThat(other.browser().download(url).status()).isEqualTo(404);
        assertThat(author.browser().download(url).status()).isEqualTo(200);
        assertThat(author.browser().delete(base(edition) + "/attachments/" + attachment.path("id").asLong()).status()).isEqualTo(204);
        assertThat(new Browser(port).download(url).status()).isEqualTo(404); // old link never becomes public
        assertThat(author.browser().fetch(url).headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    void missingAndRacingVersionsCannotOverwriteAndRestoreRequiresExpectedVersion() throws Exception {
        var author = Accounts.signedIn(port, mailbox);
        long edition = edition(author);
        long account = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(author.nick())).fetchSingle(ACCOUNT.ID);
        workspace.save(edition, "canon", "Канон", "canon", "Початковий текст", null, account, null);
        assertThat(author.browser().put(base(edition) + "/documents/canon", json("title", "Канон", "content", "Без версії")).status()).isEqualTo(409);
        CountDownLatch start = new CountDownLatch(1);
        var a = CompletableFuture.supplyAsync(() -> saveAtOnce(start, edition, account, "Перша правка"));
        var b = CompletableFuture.supplyAsync(() -> saveAtOnce(start, edition, account, "Друга правка"));
        start.countDown();
        assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        assertThat(workspace.get(edition, "canon").orElseThrow().version()).isEqualTo(2);
        assertThat(workspace.revisions(edition, "canon")).hasSize(2);
        long revision = workspace.revisions(edition, "canon").getLast().id();
        assertThat(author.browser().post(base(edition) + "/documents/canon/revisions/" + revision + "/restore?version=1", "{}").status()).isEqualTo(409);
        assertThat(author.browser().post(base(edition) + "/documents/canon/revisions/" + revision + "/restore?version=2", "{}").status()).isEqualTo(200);
    }

    private boolean saveAtOnce(CountDownLatch start, long edition, long account, String text) {
        try {
            start.await();
            workspace.save(edition, "canon", "Канон", "canon", text, 1L, account, null);
            return true;
        } catch (space.panrid.novelka.platform.web.UserFacingException conflict) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Test
    void oldNotificationsDisappearAfterRevocationAndCannotBeDeliveredToOutsiders() {
        var author = Accounts.signedIn(port, mailbox);
        var reader = Accounts.signedIn(port, mailbox);
        long edition = edition(author);
        long account = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(reader.nick())).fetchSingle(ACCOUNT.ID);
        Map<String, Object> payload = Map.of("editionId", edition, "novelTitle", "Приватна назва", "excerpt", "Секретний уривок");
        assertThat(notices.allowed(account, payload)).isFalse();
        assertThat(author.browser().post("/api/studio/editions/" + edition + "/readers", json("nick", reader.nick())).status()).isEqualTo(204);
        assertThat(notices.allowed(account, payload)).isTrue();
        db.insertInto(NOTIFICATION).set(NOTIFICATION.RECIPIENT_ID, account).set(NOTIFICATION.KIND, "mention")
                .set(NOTIFICATION.PAYLOAD, JSONB.valueOf(JSON.writeValueAsString(payload))).execute();
        assertThat(reader.browser().get("/api/notifications").body()).contains("Секретний уривок");
        assertThat(author.browser().delete("/api/studio/editions/" + edition + "/readers/" + reader.nick()).status()).isEqualTo(204);
        assertThat(reader.browser().get("/api/notifications").body()).doesNotContain("Секретний уривок", "Приватна назва");
        assertThat(notices.allowed(account, payload)).isFalse();
    }

    @Test
    void archiveContainsActualOriginalBytesAndNotes() throws Exception {
        var author = Accounts.signedIn(port, mailbox);
        long edition = edition(author);
        byte[] original = Pictures.png(500, 500, Color.ORANGE);
        JsonNode upload = read(author.browser().upload("/api/media/images", Map.of("kind", "illustration"), "file", "map.png", "image/png", original));
        long imageId = upload.path("id").asLong();
        read(author.browser().post(base(edition) + "/attachments", json("imageId", imageId, "name", "map.png")));
        read(author.browser().put(base(edition) + "/documents/canon", json("title", "Канон", "content", "Точний текст.")));
        var response = author.browser().download(base(edition) + "/export.zip");
        assertThat(response.status()).isEqualTo(200);
        Map<String, byte[]> entries = new java.util.HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertThat(entries.get("files/" + imageId + ".bin")).isEqualTo(original);
        assertThat(new String(entries.get("documents/canon.md"), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("Точний текст.");
        assertThat(entries).containsKeys("sha256.json", "workspace.json", "manuscript/chapters.json", "manuscript/revisions.json", "manuscript/drafts.json");
        assertThat(new Browser(port).download(base(edition) + "/export.zip").status()).isBetween(400, 499);
    }

    @Test
    void codeExamplesAreNotImportedAsBrokenWikiLinks() {
        var author = Accounts.signedIn(port, mailbox);
        long edition = edition(author);
        long account = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(author.nick())).fetchSingle(ACCOUNT.ID);
        var doc = workspace.save(edition, "canon", "Канон", "canon",
                "`[[Приклад]]`\n```md\n[[Теж приклад]]\n```\n[[canon]]", null, account, null);
        assertThat(doc.links()).containsExactly("canon");
    }

    @Test
    void filesWithoutAnAccessRecordAreNeverServed() throws Exception {
        java.nio.file.Files.createDirectories(mediaDirectory);
        java.nio.file.Path orphan = mediaDirectory.resolve("orphan-" + java.util.UUID.randomUUID() + ".jpg");
        try {
            java.nio.file.Files.write(orphan, Pictures.png(500, 500, Color.BLACK));
            assertThat(new Browser(port).download("/media/" + orphan.getFileName()).status()).isEqualTo(404);
        } finally {
            java.nio.file.Files.deleteIfExists(orphan);
        }
    }

    private long edition(Accounts.Person author) {
        return read(author.browser().post("/api/studio/editions", json("kind", "original", "title", "Тестовий твір " + java.util.UUID.randomUUID(), "description", java.util.List.of(), "tags", java.util.List.of())))
                .path("editionId").asLong();
    }

    private static String base(long edition) { return "/api/studio/editions/" + edition + "/workspace"; }
    private static JsonNode read(Browser.Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
