package panrid.space.novelka.core;

import static org.junit.jupiter.api.Assertions.*;
import static panrid.space.novelka.core.Domain.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class PipelineIntegrationTest {
  static EmbeddedPostgres postgres;
  Store store;
  String novel;

  @BeforeAll
  static void start() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
  }

  @AfterAll
  static void stop() throws Exception {
    if (postgres != null) postgres.close();
  }

  @BeforeEach
  void setup() throws Exception {
    store = new Store(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "");
    novel = "n" + Math.abs(UUID.randomUUID().getLeastSignificantBits()) + "a";
    store.save(new Novel(novel, "題", "作者", "https://ncode.syosetu.com/" + novel + "/", 2, false));
    store.save(
        novel,
        new Chapter(
            1,
            "url",
            "題",
            List.of(new Block("title", "heading", "題"), new Block("p1", "paragraph", "彼の名前はアキです。")),
            "html"));
  }

  @AfterEach
  void close() throws Exception {
    store.close();
  }

  @Test
  void resumesFromSavedDraftAndKeepsRevisions() throws Exception {
    AtomicInteger analysis = new AtomicInteger(),
        translate = new AtomicInteger(),
        edit = new AtomicInteger();
    AiClient ai =
        (w, i, stage, g, p, budget) -> {
          if (stage.equals("analyze")) {
            analysis.incrementAndGet();
            return Json.read("{\"entries\":[]}");
          }
          if (stage.equals("translate")) translate.incrementAndGet();
          if (stage.equals("proofread") && edit.incrementAndGet() == 1)
            throw new IllegalStateException("interrupted");
          return Json.read(
              "{\"blocks\":[{\"id\":\"title\",\"kind\":\"heading\",\"text\":\"Заголовок\"},{\"id\":\"p1\",\"kind\":\"paragraph\",\"text\":\"Його"
                  + " звати Акі.\"}],\"summary\":\"Акі представився.\"}");
        };
    var p = new Pipeline(store, ai, 6000);
    var w = p.create(novel, 1, false);
    assertThrows(IllegalStateException.class, () -> p.run(w, 0));
    assertEquals("translated", store.job(w.id()).segments().getFirst().state());
    var done = p.run(store.job(w.id()), 0);
    assertEquals("complete", done.state());
    assertEquals(1, analysis.get());
    assertEquals(1, translate.get());
    assertEquals(2, edit.get());
    assertEquals(w.id(), p.create(novel, 1, false).id());
    assertEquals(2, p.create(novel, 1, true).revision());
    assertEquals(0, store.completed(novel).size());
  }

  @Test
  void storesManualFactsAndInvalidatesOnlyUsedEntries() throws Exception {
    var e =
        new Entry(
            "アキ", "character", "アキ", "あき", "Акі", List.of(), "unknown", "", "unknown", 1, false);
    store.glossary(novel, new Glossary(1, List.of(e)));
    var p = new Pipeline(store, null, 6000);
    var w = p.create(novel, 1, false);
    store.save(new Work(w.id(), novel, 1, w.sourceHash(), 1, w.segments(), "complete", ""));
    store.start(
        new Call(
            "call" + novel,
            w.id(),
            "translate",
            0,
            "test",
            "v1",
            1,
            Json.write(
                Map.of(
                    "messages",
                    List.of(Map.of("content", Json.write(Map.of("dictionary", List.of(e))))))),
            0.001,
            "pending"));
    var changed =
        new Entry(
            "アキ",
            "character",
            "アキ",
            "あき",
            "Акі",
            List.of(),
            "female",
            "explicit evidence",
            "confirmed",
            1,
            true);
    store.glossary(novel, new Glossary(2, List.of(changed)));
    assertEquals("needs-review", store.job(w.id()).state());
    assertEquals(
        2, store.rows("SELECT revision FROM glossary_versions WHERE novel_id=?", novel).size());
  }

  @Test
  void newFactsDoNotInvalidateEarlierChapters() throws Exception {
    var w = new Pipeline(store, null, 6000).create(novel, 1, false);
    store.save(new Work(w.id(), novel, 1, w.sourceHash(), 1, w.segments(), "complete", ""));
    store.glossary(
        novel,
        new Glossary(
            1,
            List.of(
                new Entry(
                    "新", "term", "新", "", "Новий", List.of(), "unknown", "", "unknown", 1,
                    false))));
    assertEquals("complete", store.job(w.id()).state());
  }

  @Test
  void changedSourceRequiresExplicitNewRevision() throws Exception {
    var p = new Pipeline(store, null, 6000);
    p.create(novel, 1, false);
    store.save(
        novel,
        new Chapter(1, "url", "new", List.of(new Block("title", "heading", "new")), "new html"));
    assertThrows(IllegalStateException.class, () -> p.create(novel, 1, false));
    assertEquals(2, p.create(novel, 1, true).revision());
    assertEquals(2, store.rows("SELECT data FROM chapter_versions WHERE novel_id=?", novel).size());
  }
}
