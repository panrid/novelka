package panrid.space.novelka.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import panrid.space.novelka.core.model.*;

class ContentTest {
  @TempDir Path temp;

  @Test
  void normalizesAndValidatesNovelAliases() {
    assertEquals("водний-маг", Store.normalizeAlias(" Водний-Маг "));
    assertEquals("water.mag_2", Store.normalizeAlias("Water.Mag_2"));
    assertThrows(IllegalArgumentException.class, () -> Store.normalizeAlias("two words"));
    assertThrows(IllegalArgumentException.class, () -> Store.normalizeAlias("-starts-with-dash"));
  }

  @Test
  void requestJsonIsStableAcrossMapInsertionOrders() {
    var a = new LinkedHashMap<String, Object>();
    a.put("source", List.of("本文"));
    a.put("context", "scene");
    var b = new LinkedHashMap<String, Object>();
    b.put("context", "scene");
    b.put("source", List.of("本文"));
    assertEquals(Json.write(a), Json.write(b));
  }

  @Test
  void structuredTranslationRequiresEveryIdAndRestoresOrder() {
    var source = List.of(new Block("a", "heading", "題"), new Block("b", "paragraph", "本文"));
    var payload = Map.of("source", source);
    var schema = Json.M.valueToTree(OpenRouter.responseFormat("translate", payload));
    assertTrue(schema.path("json_schema").path("strict").asBoolean());
    assertEquals(
        2,
        schema
            .path("json_schema")
            .path("schema")
            .path("properties")
            .path("translations")
            .path("required")
            .size());
    var normalized =
        OpenRouter.normalize(
            "translate",
            payload,
            Json.read("{\"translations\":{\"b\":\"Текст\",\"a\":\"Заголовок\"}}"));
    assertEquals("a", normalized.path("blocks").get(0).path("id").asText());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OpenRouter.normalize(
                "translate", payload, Json.read("{\"translations\":{\"a\":\"Заголовок\"}}")));
  }

  @Test
  void importsUserTextWithoutFetchingRemoteContent() {
    var ch =
        PlainText.parse(1, "file:///chapter.txt", "\uFEFF0000 プロローグ\r\n\r\n「涼さん」\r\n本文\r\n＊＊＊");
    assertEquals("0000 プロローグ", ch.title());
    assertEquals(4, ch.blocks().size());
    assertEquals("separator", ch.blocks().getLast().kind());
    assertEquals("", ch.rawHtml());
    assertThrows(
        IllegalArgumentException.class, () -> PlainText.parse(1, "file:///empty.txt", "title"));
  }

  @Test
  void parsesModernAndLegacyPagesWithRubyAndNotes() {
    var ch =
        Syosetu.parse(
            1,
            "https://ncode.syosetu.com/n1a/1/",
            "<h1 class='p-novel__title'>題</h1><div class='p-novel__text"
                + " p-novel__text--preface'><p>前</p></div><div"
                + " class='p-novel__text'><p>彼は<ruby>昴<rt>スバル</rt></ruby>。<br>次</p><p>＊＊＊</p></div><div"
                + " class='p-novel__text p-novel__text--afterword'><p>後</p></div>");
    assertEquals(5, ch.blocks().size());
    assertTrue(ch.blocks().get(2).text().contains("昴〔スバル〕"));
    assertTrue(ch.blocks().get(2).text().contains("\n"));
    assertEquals("separator", ch.blocks().get(3).kind());
    assertEquals(
        2,
        Syosetu.parse(
                1,
                "https://ncode.syosetu.com/n1a/",
                "<h1 class='novel_title'>題</h1><div id='novel_honbun'><p>本文</p></div>")
            .blocks()
            .size());
    assertThrows(IllegalArgumentException.class, () -> Syosetu.parse(1, "", "<h1>blocked</h1>"));
  }

  @Test
  void validatesSourceUrlsAndSegmentIdentity() {
    assertEquals("n123ab", Syosetu.code("https://ncode.syosetu.com/n123ab/5/"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Syosetu.code("https://ncode.syosetu.com.evil.test/n123ab/"));
    var blocks = List.of(new Block("1", "paragraph", "1234"), new Block("2", "paragraph", "5678"));
    assertEquals(2, Segments.split(blocks, 5).size());
    assertThrows(IllegalArgumentException.class, () -> Segments.split(blocks, 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> Segments.validate(blocks, List.of(blocks.getLast(), blocks.getFirst())));
  }

  @Test
  void dictionaryIsBoundedAndSearchesRelationships() {
    var e =
        new Entry(
            "兄",
            "character",
            "兄",
            "あに",
            "Брат",
            List.of("兄さん"),
            "unknown",
            "старший брат Акі",
            "assumed",
            1,
            true);
    var g = new Glossary(1, List.of(e));
    assertEquals(List.of(e), Dictionary.select(g, "兄さん", 5000));
    assertTrue(Dictionary.select(g, "兄さん", 2).isEmpty());
    assertEquals(List.of(e), Dictionary.search(g, "Акі", 5000));
  }

  @Test
  void exportsEscapedHtmlAndWellFormedEpub() throws Exception {
    var blocks =
        List.of(
            new Block("title", "heading", "Розділ <1>"),
            new Block("p1", "paragraph", "Текст & ще"));
    var work =
        new Work(
            "id",
            "n1a",
            1,
            "hash",
            1,
            List.of(new Segment(blocks, blocks, blocks, "", "complete")),
            "complete",
            "");
    var n = new Novel("n1a", "Назва & <тест>", "Автор", "https://ncode.syosetu.com/n1a/", 1, true);
    var exporter = new BookExporter();
    Path html = temp.resolve("book.html"), epub = temp.resolve("book.epub");
    exporter.export(n, List.of(work), html, "html");
    exporter.export(n, List.of(work), epub, "epub");
    assertTrue(Files.readString(html).contains("Текст &amp; ще"));
    try (var zip = new ZipFile(epub.toFile())) {
      assertEquals("mimetype", zip.entries().nextElement().getName());
      assertEquals(ZipEntry.STORED, zip.getEntry("mimetype").getMethod());
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      var parser = factory.newDocumentBuilder();
      for (var entry : Collections.list(zip.entries()))
        if (entry.getName().matches(".*\\.(xml|xhtml|opf)"))
          try (var in = zip.getInputStream(entry)) {
            parser.parse(in);
          }
    }
    assertThrows(IllegalArgumentException.class, () -> exporter.export(n, List.of(), html, "html"));
  }
}
