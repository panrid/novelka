package panrid.space.novelka.core;

import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.model.Work;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BookExporter {
    private static final String CSS =
            "body{max-width:44rem;margin:2rem auto;padding:0 1rem;font:1.15rem/1.8"
                    + " Georgia,serif;color:#242424;background:#faf8f3}h1,h2{line-height:1.3}p{white-space:pre-wrap}aside{font-size:.9em;color:#555}article{margin-top:4rem}nav"
                    + " a{display:block}hr{width:30%;margin:2rem auto}";

    public static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String title(Work w) {
        return w.segments().stream()
                .flatMap(s -> s.revised().stream())
                .filter(b -> b.kind().equals("heading"))
                .map(Block::text)
                .findFirst()
                .orElse("Глава " + w.chapter());
    }

    private static String content(Work w) {
        var out = new StringBuilder();
        for (var segment : w.segments())
            for (var b : segment.revised()) {
                String tag =
                        switch (b.kind()) {
                            case "heading" -> "h2";
                            case "preface", "afterword" -> "aside";
                            default -> "p";
                        };
                if (b.kind().equals("separator")) {
                    out.append("<hr/>");
                    continue;
                }
                out.append("<")
                        .append(tag)
                        .append(" id=\"")
                        .append(escape(b.id()))
                        .append("\">")
                        .append(escape(b.text()))
                        .append("</")
                        .append(tag)
                        .append(">");
            }
        return out.toString();
    }

    public void export(Novel n, List<Work> chapters, Path path, String format) throws Exception {
        if (chapters.isEmpty())
            throw new IllegalArgumentException("No fully proofread chapters available for export");
        if (chapters.stream().anyMatch(w -> !w.state().equals("complete")))
            throw new IllegalArgumentException("Only complete jobs can be exported");
        if (!Set.of("html", "epub").contains(format))
            throw new IllegalArgumentException("Format must be html or epub");
        Path dest = path.toAbsolutePath();
        Files.createDirectories(dest.getParent());
        Path tmp = Files.createTempFile(dest.getParent(), ".novelka-", ".tmp");
        try {
            if (format.equals("html")) html(n, chapters, tmp);
            else epub(n, chapters, tmp);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void html(Novel n, List<Work> chapters, Path path) throws Exception {
        StringBuilder out =
                new StringBuilder(
                        "<!DOCTYPE html><html lang=\"uk\"><head><meta charset=\"utf-8\"/><meta"
                                + " name=\"viewport\" content=\"width=device-width, initial-scale=1\"/><title>"
                                + escape(n.title())
                                + "</title><style>"
                                + CSS
                                + "</style></head><body><h1>"
                                + escape(n.title())
                                + "</h1><p>"
                                + escape(n.author())
                                + "</p><p><a href=\""
                                + escape(n.url())
                                + "\">Оригінал</a></p><nav>");
        for (var w : chapters)
            out.append("<a href=\"#chapter-")
                    .append(w.chapter())
                    .append("\">")
                    .append(escape(title(w)))
                    .append("</a>");
        out.append("</nav>");
        for (var w : chapters)
            out.append("<article id=\"chapter-")
                    .append(w.chapter())
                    .append("\">")
                    .append(content(w).replace("id=\"", "id=\"c" + w.chapter() + "-"))
                    .append("</article>");
        Files.writeString(path, out.append("</body></html>").toString());
    }

    private void epub(Novel n, List<Work> chapters, Path path) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            var first = new ZipEntry("mimetype");
            first.setMethod(ZipEntry.STORED);
            first.setSize(mime.length);
            var crc = new CRC32();
            crc.update(mime);
            first.setCrc(crc.getValue());
            zip.putNextEntry(first);
            zip.write(mime);
            zip.closeEntry();
            entry(
                    zip,
                    "META-INF/container.xml",
                    "<?xml version=\"1.0\"?><container version=\"1.0\""
                            + " xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile"
                            + " full-path=\"OEBPS/package.opf\""
                            + " media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            entry(zip, "OEBPS/style.css", CSS);
            StringBuilder nav = new StringBuilder("<nav epub:type=\"toc\" id=\"toc\"><h1>Зміст</h1><ol>"),
                    manifest =
                            new StringBuilder(
                                    "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\""
                                            + " properties=\"nav\"/><item id=\"css\" href=\"style.css\""
                                            + " media-type=\"text/css\"/>"),
                    spine = new StringBuilder();
            for (var w : chapters) {
                String id = "ch" + w.chapter();
                entry(zip, "OEBPS/" + id + ".xhtml", xhtml(title(w), content(w)));
                nav.append("<li><a href=\"")
                        .append(id)
                        .append(".xhtml\">")
                        .append(escape(title(w)))
                        .append("</a></li>");
                manifest
                        .append("<item id=\"")
                        .append(id)
                        .append("\" href=\"")
                        .append(id)
                        .append(".xhtml\" media-type=\"application/xhtml+xml\"/>");
                spine.append("<itemref idref=\"").append(id).append("\"/>");
            }
            entry(zip, "OEBPS/nav.xhtml", xhtml(n.title(), nav.append("</ol></nav>").toString()));
            entry(
                    zip,
                    "OEBPS/package.opf",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package"
                            + " xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\""
                            + " unique-identifier=\"book-id\"><metadata"
                            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier"
                            + " id=\"book-id\">urn:novelka:"
                            + escape(n.id())
                            + "</dc:identifier><dc:title>"
                            + escape(n.title())
                            + "</dc:title><dc:creator>"
                            + escape(n.author())
                            + "</dc:creator><dc:language>uk</dc:language><dc:source>"
                            + escape(n.url())
                            + "</dc:source><meta property=\"dcterms:modified\">"
                            + java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                            + "</meta></metadata><manifest>"
                            + manifest
                            + "</manifest><spine>"
                            + spine
                            + "</spine></package>");
        }
    }

    private static String xhtml(String title, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\""
                + " xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"uk\""
                + " xml:lang=\"uk\"><head><title>"
                + escape(title)
                + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body>"
                + body
                + "</body></html>";
    }

    private static void entry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
