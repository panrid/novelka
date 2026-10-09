package space.panrid.novelka.reading.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.media.ImageFile;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;

class EpubBookTests {

    @Test
    void groupsChaptersByVolumesKeepsPicturesAndEscapesText() throws Exception {
        ImageFile picture = new ImageFile(new byte[] {1, 2, 3}, "image/png", "png");
        List<EpubBook.Chapter> chapters = List.of(
                new EpubBook.Chapter(1, null, "Початок", List.of(Block.paragraph("a", List.of(Span.plain("Том & <сцена>")))), "Том 1"),
                new EpubBook.Chapter(2, "", "Інтерлюдія", List.of(new Block("b", "image", List.of(), 7L, null)), "Том 1"),
                new EpubBook.Chapter(3, null, "", List.of(Block.separator("c")), "Том 2. Море"));
        byte[] epub = EpubBook.write(new EpubBook.Book("urn:novelka:1", "Маг води", null, "Тадаші Хісахо",
                List.of("Тадаші Хісахо"), null, chapters, Map.of(7L, picture)));

        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(epub))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        String nav = text(files, "OEBPS/nav.xhtml");
        assertThat(nav).contains(">Том 1</a><ol>", ">Том 2. Море</a><ol>", ">Інтерлюдія</a>", ">Глава 3</a>");
        assertThat(text(files, "OEBPS/chapter-1.xhtml")).contains("<p>Том &amp; &lt;сцена&gt;</p>");
        assertThat(text(files, "OEBPS/chapter-2.xhtml")).contains("<img src=\"images/7.png\"");
        assertThat(files.get("OEBPS/images/7.png")).containsExactly(1, 2, 3);
        assertThat(text(files, "OEBPS/content.opf")).contains("href=\"images/7.png\" media-type=\"image/png\"",
                "<dc:creator>Тадаші Хісахо</dc:creator>");
    }

    private static String text(Map<String, byte[]> files, String name) {
        return new String(files.get(name), StandardCharsets.UTF_8);
    }
}
