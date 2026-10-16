package space.panrid.novelka.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;

class OriginalFilesTests {

    @Test
    void pastedTextSplitsAtTheHeadingsOfAnyLanguage() {
        List<OriginalFiles.Chapter> chapters = OriginalFiles.parseText("""
                プロローグ
                灯台の火が消えた夜。

                第１話　灯台守
                彼は目を覚ました。
                ◇◇◇
                朝が来た。
                第十二章 归来
                他回来了。
                Chapter 3: The Return
                He came back.
                제4화 귀환
                그가 돌아왔다.
                """);
        assertThat(chapters).extracting(OriginalFiles.Chapter::title)
                .containsExactly("プロローグ", "第１話　灯台守", "第十二章 归来", "Chapter 3: The Return", "제4화 귀환");
        assertThat(chapters.get(1).blocks()).extracting(Block::type).containsExactly("paragraph", "separator", "paragraph");
        assertThat(chapters.get(1).chars()).as("characters without spaces").isEqualTo("彼は目を覚ました。朝が来た。".length());
    }

    @Test
    void textWithoutHeadingsIsOneChapterTitledByItsShortFirstLine() {
        List<OriginalFiles.Chapter> one = OriginalFiles.parseText("The Lighthouse Keeper\nThe night the light went out.\nHe woke.");
        assertThat(one).hasSize(1);
        assertThat(one.getFirst().title()).isEqualTo("The Lighthouse Keeper");
        assertThat(one.getFirst().blocks()).extracting(Block::text).containsExactly("The night the light went out.", "He woke.");
        assertThatThrownBy(() -> OriginalFiles.parseText("  \n\n ")).isInstanceOf(UserFacingException.class);
    }

    @Test
    void shiftJisTextFromWindowsIsRead() {
        byte[] sjis = "第1話 夜\n灯台の火が消えた。".getBytes(java.nio.charset.Charset.forName("windows-31j"));
        OriginalFiles.Chapter chapter = OriginalFiles.parse("novel.txt", sjis).chapters().getFirst();
        assertThat(chapter.title()).isEqualTo("第1話 夜");
        assertThat(chapter.blocks().getFirst().text()).isEqualTo("灯台の火が消えた。");
    }

    @Test
    void anEpubGivesItsChaptersInReadingOrderWithPicturesAndWithoutFurigana() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("META-INF/container.xml", """
                <?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""");
        files.put("OEBPS/content.opf", """
                <?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><manifest>
                <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                <item id="c0" href="text/cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="plate" href="text/plate.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="art" href="images/art.png" media-type="image/png"/>
                </manifest><spine><itemref idref="c0"/><itemref idref="plate"/><itemref idref="c1"/><itemref idref="c2"/></spine></package>""");
        files.put("OEBPS/text/cover.xhtml", "<html><body><img src=\"../images/cover.jpg\"/></body></html>");
        files.put("OEBPS/text/plate.xhtml", "<html><body><img src=\"../images/art.png\"/></body></html>");
        files.put("OEBPS/text/ch1.xhtml", """
                <html><head><title>一</title></head><body><h1>第一話 灯台守</h1>
                <p><ruby>灯台<rt>とうだい</rt></ruby>の火が消えた。</p><hr/><p>朝が来た。</p></body></html>""");
        files.put("OEBPS/text/ch2.xhtml", "<html><body><h2>第二話</h2><p>彼は戻った。</p></body></html>");
        byte[] epub = zip(files, Map.of("OEBPS/images/cover.jpg", new byte[] {1, 2}, "OEBPS/images/art.png", new byte[] {3, 4}));

        OriginalFiles.Parsed parsed = OriginalFiles.parse("book.epub", epub);
        assertThat(parsed.chapters()).extracting(OriginalFiles.Chapter::title).containsExactly("第一話 灯台守", "第二話");
        OriginalFiles.Chapter first = parsed.chapters().getFirst();
        assertThat(first.blocks()).extracting(Block::type).as("the plate opens the first chapter; the cover is not kept")
                .containsExactly("image", "paragraph", "separator", "paragraph");
        assertThat(first.blocks().getFirst().sourceUrl()).isEqualTo("epub:OEBPS/images/art.png");
        assertThat(first.blocks().get(1).text()).isEqualTo("灯台の火が消えた。");
        assertThat(first.pictures()).isEqualTo(1);
        assertThat(parsed.pictures()).containsKey("OEBPS/images/art.png");
    }

    @Test
    void onlyTextTxtAndEpubAreTaken() {
        assertThatThrownBy(() -> OriginalFiles.parse("book.pdf", new byte[] {1})).isInstanceOf(UserFacingException.class);
        assertThatThrownBy(() -> OriginalFiles.parse("book.epub", "not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UserFacingException.class);
    }

    private static byte[] zip(Map<String, String> texts, Map<String, byte[]> binaries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> file : texts.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (Map.Entry<String, byte[]> file : binaries.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
