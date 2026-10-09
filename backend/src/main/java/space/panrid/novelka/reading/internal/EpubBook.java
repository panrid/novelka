package space.panrid.novelka.reading.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import space.panrid.novelka.media.ImageFile;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;

/**
 * A translation as an EPUB 3 book: a cover, a title page, the contents by volumes and the
 * chapters with their pictures. Readers that know only EPUB 2 find the old table of contents too.
 */
final class EpubBook {

    /** @param label the number readers see; null means {@code number}, empty means none */
    record Chapter(int number, String label, String title, List<Block> blocks, String volume) {

        String heading() {
            String shown = label == null ? String.valueOf(number) : label;
            if (title == null || title.isBlank()) {
                return shown.isEmpty() ? "Без назви" : "Глава " + shown;
            }
            return shown.isEmpty() ? title : shown + ". " + title;
        }
    }

    /**
     * @param id      a stable identifier of the book, the same for every download of it
     * @param part    the volume's name when the book holds one volume, else null
     * @param about   lines of the title page under the title: author, translation, team, the site
     * @param images  pictures of the chapters by image id; missing ones are left out
     */
    record Book(String id, String title, String part, String author, List<String> about, ImageFile cover,
            List<Chapter> chapters, Map<Long, ImageFile> images) {
    }

    /** One file of the book as the package lists it. */
    private record Item(String id, String href, String mediaType, String properties) {
    }

    private static final String XHTML = "application/xhtml+xml";

    private EpubBook() {
    }

    static byte[] write(Book book) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            // The first entry is the type, stored uncompressed, as the format asks.
            byte[] mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry type = new ZipEntry("mimetype");
            type.setMethod(ZipEntry.STORED);
            type.setSize(mimetype.length);
            CRC32 crc = new CRC32();
            crc.update(mimetype);
            type.setCrc(crc.getValue());
            zip.putNextEntry(type);
            zip.write(mimetype);
            zip.closeEntry();

            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            put(zip, "OEBPS/style.css", STYLE);

            List<Item> manifest = new ArrayList<>();
            List<String> spine = new ArrayList<>();
            if (book.cover() != null) {
                String name = "images/cover." + book.cover().extension();
                put(zip, "OEBPS/" + name, book.cover().content());
                manifest.add(new Item("cover-image", name, book.cover().mediaType(), "cover-image"));
                put(zip, "OEBPS/cover.xhtml", page("Обкладинка",
                        "<div class=\"cover\"><img src=\"" + name + "\" alt=\"" + escape(book.title()) + "\"/></div>"));
                manifest.add(new Item("cover", "cover.xhtml", XHTML, null));
                spine.add("cover");
            }
            put(zip, "OEBPS/title.xhtml", page(book.title(), titlePage(book)));
            manifest.add(new Item("title", "title.xhtml", XHTML, null));
            spine.add("title");

            for (Map.Entry<Long, ImageFile> image : book.images().entrySet()) {
                String name = imageName(image.getKey(), image.getValue());
                put(zip, "OEBPS/" + name, image.getValue().content());
                manifest.add(new Item("image-" + image.getKey(), name, image.getValue().mediaType(), null));
            }
            List<Chapter> chapters = book.chapters();
            for (int i = 0; i < chapters.size(); i++) {
                Chapter chapter = chapters.get(i);
                String id = "chapter-" + (i + 1);
                put(zip, "OEBPS/" + id + ".xhtml", page(chapter.heading(), chapterBody(chapter, book.images())));
                manifest.add(new Item(id, id + ".xhtml", XHTML, null));
                spine.add(id);
            }
            put(zip, "OEBPS/nav.xhtml", nav(book));
            manifest.add(new Item("nav", "nav.xhtml", XHTML, "nav"));
            put(zip, "OEBPS/toc.ncx", ncx(book));
            manifest.add(new Item("ncx", "toc.ncx", "application/x-dtbncx+xml", null));
            manifest.add(new Item("style", "style.css", "text/css", null));
            put(zip, "OEBPS/content.opf", opf(book, manifest, spine));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return bytes.toByteArray();
    }

    private static String titlePage(Book book) {
        StringBuilder out = new StringBuilder("<div class=\"title-page\"><h1>").append(escape(book.title())).append("</h1>");
        if (book.part() != null) {
            out.append("<p class=\"part\">").append(escape(book.part())).append("</p>");
        }
        for (String line : book.about()) {
            out.append("<p>").append(escape(line)).append("</p>");
        }
        return out.append("</div>").toString();
    }

    private static String chapterBody(Chapter chapter, Map<Long, ImageFile> images) {
        StringBuilder out = new StringBuilder();
        if (chapter.volume() != null) {
            out.append("<p class=\"volume\">").append(escape(chapter.volume())).append("</p>");
        }
        out.append("<h1>").append(escape(chapter.heading())).append("</h1>\n");
        for (Block block : chapter.blocks()) {
            switch (block.type()) {
                case "separator" -> out.append("<p class=\"separator\">◇</p>\n");
                case "image" -> {
                    ImageFile file = block.imageId() == null ? null : images.get(block.imageId());
                    if (file != null) {
                        out.append("<div class=\"figure\"><img src=\"").append(imageName(block.imageId(), file))
                                .append("\" alt=\"\"/></div>\n");
                    }
                }
                case "heading" -> out.append("<h2>").append(spans(block.content())).append("</h2>\n");
                case "paragraph" -> out.append("<p>").append(spans(block.content())).append("</p>\n");
                default -> out.append("<p class=\"aside\">").append(spans(block.content())).append("</p>\n");
            }
        }
        return out.toString();
    }

    private static String spans(List<Span> spans) {
        StringBuilder out = new StringBuilder();
        for (Span span : spans) {
            String text = escape(span.text());
            if (span.marks().contains("strike")) text = "<s>" + text + "</s>";
            if (span.marks().contains("underline")) text = "<u>" + text + "</u>";
            if (span.marks().contains("italic")) text = "<em>" + text + "</em>";
            if (span.marks().contains("bold")) text = "<strong>" + text + "</strong>";
            out.append(text);
        }
        return out.toString();
    }

    /** The contents for EPUB 3: chapters under their volumes when there are any. */
    private static String nav(Book book) {
        StringBuilder out = new StringBuilder("<nav epub:type=\"toc\" id=\"toc\"><h1>Зміст</h1>\n<ol>\n");
        String volume = null;
        boolean open = false;
        for (int i = 0; i < book.chapters().size(); i++) {
            Chapter chapter = book.chapters().get(i);
            if (chapter.volume() != null && !chapter.volume().equals(volume) && book.part() == null) {
                if (open) out.append("</ol></li>\n");
                volume = chapter.volume();
                out.append("<li><a href=\"chapter-").append(i + 1).append(".xhtml\">").append(escape(volume)).append("</a><ol>\n");
                open = true;
            }
            out.append("<li><a href=\"chapter-").append(i + 1).append(".xhtml\">").append(escape(chapter.heading())).append("</a></li>\n");
        }
        if (open) out.append("</ol></li>\n");
        out.append("</ol></nav>");
        return page("Зміст", out.toString());
    }

    private static String ncx(Book book) {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < book.chapters().size(); i++) {
            points.append("<navPoint id=\"p").append(i + 1).append("\" playOrder=\"").append(i + 1).append("\"><navLabel><text>")
                    .append(escape(book.chapters().get(i).heading())).append("</text></navLabel><content src=\"chapter-")
                    .append(i + 1).append(".xhtml\"/></navPoint>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head><meta name="dtb:uid" content="%s"/></head>
                <docTitle><text>%s</text></docTitle>
                <navMap>
                %s</navMap>
                </ncx>
                """.formatted(escape(book.id()), escape(fullTitle(book)), points);
    }

    private static String opf(Book book, List<Item> manifest, List<String> spine) {
        StringBuilder items = new StringBuilder();
        manifest.forEach(item -> items.append("    <item id=\"").append(item.id()).append("\" href=\"").append(item.href())
                .append("\" media-type=\"").append(item.mediaType()).append('"')
                .append(item.properties() == null ? "" : " properties=\"" + item.properties() + "\"").append("/>\n"));
        StringBuilder order = new StringBuilder();
        spine.forEach(id -> order.append("    <itemref idref=\"").append(id).append("\"/>\n"));
        String modified = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="uk">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">%s</dc:identifier>
                    <dc:title>%s</dc:title>
                    <dc:language>uk</dc:language>
                %s    <meta property="dcterms:modified">%s</meta>
                %s  </metadata>
                  <manifest>
                %s  </manifest>
                  <spine toc="ncx">
                %s  </spine>
                </package>
                """.formatted(escape(book.id()), escape(fullTitle(book)),
                book.author() == null || book.author().isBlank() ? "" : "    <dc:creator>" + escape(book.author()) + "</dc:creator>\n",
                modified, book.cover() == null ? "" : "    <meta name=\"cover\" content=\"cover-image\"/>\n", items, order);
    }

    private static String fullTitle(Book book) {
        return book.part() == null ? book.title() : book.title() + ". " + book.part();
    }

    private static String page(String title, String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="uk" lang="uk">
                <head><meta charset="utf-8"/><title>%s</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
                <body>
                %s
                </body>
                </html>
                """.formatted(escape(title), body);
    }

    private static String imageName(long id, ImageFile file) {
        return "images/" + id + "." + file.extension();
    }

    /** Text for XML: the five special characters, and nothing XML forbids. */
    static String escape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(c -> {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> {
                    if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD) || c > 0xFFFF) {
                        out.appendCodePoint(c);
                    }
                }
            }
        });
        return out.toString();
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        put(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static final String STYLE = """
            body { margin: 0 4%; line-height: 1.55; }
            h1 { font-size: 1.4em; margin: 1.5em 0 1em; text-align: center; }
            h2 { font-size: 1.15em; margin: 1.2em 0 0.6em; }
            p { margin: 0 0 0.6em; text-indent: 1.2em; text-align: justify; }
            p.aside { font-style: italic; text-indent: 0; }
            p.separator { text-align: center; text-indent: 0; margin: 1em 0; }
            p.volume { text-align: center; text-indent: 0; font-size: 0.9em; margin-top: 2em; }
            .cover { text-align: center; }
            .cover img { max-width: 100%; max-height: 100%; }
            .figure { text-align: center; margin: 1em 0; }
            .figure img { max-width: 100%; }
            .title-page { text-align: center; margin-top: 20%; }
            .title-page p { text-indent: 0; text-align: center; }
            .title-page .part { font-size: 1.2em; margin-bottom: 2em; }
            """;
}
