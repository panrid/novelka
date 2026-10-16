package space.panrid.novelka.text;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * The original of chapters a translator pastes or uploads (етап 19): plain text, a .txt file or
 * an .epub, one chapter or many. The text is kept as it is — only split into chapters and
 * paragraphs; the original is for the translation, readers never see it.
 *
 * <ul>
 *   <li>Text and .txt: a chapter starts at a heading line — «第12話», «第十二章», «제12화»,
 *       «Chapter 12», «Глава 12», «プロローグ», «Prologue» and the like. Every other non-empty
 *       line is a paragraph; «◇◇◇» or «* * *» is a scene break. Without headings the whole text
 *       is one chapter, and its first short line is the title.</li>
 *   <li>.epub: the documents of the book in reading order, each a chapter; its first heading
 *       (or the document's title) is the title, its paragraphs the text. Furigana is dropped,
 *       documents without text (the cover, the contents) are skipped.</li>
 * </ul>
 */
public final class OriginalFiles {

    public record Chapter(String title, List<Block> blocks, int chars) {
        public int pictures() {
            return (int) blocks.stream().filter(block -> block.type().equals("image")).count();
        }
    }

    /**
     * @param pictures the pictures of an .epub by their path in it; a chapter's picture is a block
     *                 of type {@code image} whose {@code sourceUrl} is {@code epub:<path>}, stored
     *                 on the site when the original is saved
     */
    public record Parsed(List<Chapter> chapters, Map<String, byte[]> pictures) {
    }

    public static final String EPUB_PICTURE = "epub:";

    public static final int MAX_CHAPTERS = ChapterFiles.MAX_CHAPTERS;
    public static final int MAX_CHAPTER_CHARS = ChapterFiles.MAX_CHAPTER_CHARS;
    private static final int TITLE_MAX = 200;
    /** What an .epub may unpack to: books are a few megabytes of text; more is not a book. */
    private static final long EPUB_MAX_UNPACKED = 80L * 1024 * 1024;
    private static final int EPUB_MAX_ENTRIES = 5_000;

    private static final String NUMBER = "[0-9０-９一二三四五六七八九十百千零〇两兩]+";
    private static final Pattern HEADING = Pattern.compile(String.join("|",
            "^第\\s*" + NUMBER + "\\s*[話话章回節节部幕]([\\s　:：.．「『（(【\\-—―].*)?$",
            "^제?\\s*\\d+\\s*[화장](\\s.*)?$",
            "^(chapter|ch\\.|episode|ep\\.|part)\\s*\\d+([\\s:.\\-—–].*)?$",
            "^(глава|розділ|частина)\\s+\\d+.*$",
            "^(プロローグ|エピローグ|序章|終章|幕間|间章|間章|序幕|尾声|尾聲|프롤로그|에필로그|prologue|epilogue|interlude)([\\s　:：「『（(].*)?$"),
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SEPARATOR = Pattern.compile("^([*＊◇◆□■○●☆★◎※―—\\-_=~・]\\s*){3,}$|^[◇◆]+$");

    private OriginalFiles() {
    }

    /** {@code fileName} null means pasted text. */
    public static Parsed parse(String fileName, byte[] content) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        List<Chapter> chapters;
        Map<String, byte[]> pictures = Map.of();
        if (lower.endsWith(".epub")) {
            Map<String, byte[]> files = unzip(content);
            chapters = epub(files);
            pictures = files.entrySet().stream().filter(file -> picture(file.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else if (fileName == null || lower.endsWith(".txt")) {
            chapters = text(decode(content), fileName == null ? "Глава" : baseName(fileName));
        } else {
            throw UserFacingException.badRequest("Підходять вставлений текст, файли .txt і .epub.");
        }
        if (chapters.isEmpty()) {
            throw UserFacingException.badRequest("Тут немає тексту.");
        }
        if (chapters.size() > MAX_CHAPTERS) {
            throw UserFacingException.badRequest("Забагато глав за раз: до %d.".formatted(MAX_CHAPTERS));
        }
        for (Chapter chapter : chapters) {
            if (chapter.chars() > MAX_CHAPTER_CHARS) {
                throw UserFacingException.badRequest("Глава «%s» задовга: до %d знаків.".formatted(chapter.title(), MAX_CHAPTER_CHARS));
            }
        }
        return new Parsed(chapters, pictures);
    }

    public static List<Chapter> text(String content, String fallbackTitle) {
        String text = content.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
        List<Draft> drafts = new ArrayList<>();
        Draft current = null;
        for (String raw : text.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (HEADING.matcher(line).matches()) {
                current = new Draft(title(line));
                drafts.add(current);
                continue;
            }
            if (current == null) {
                current = new Draft(null);
                drafts.add(current);
            }
            current.add(line);
        }
        List<Chapter> chapters = new ArrayList<>();
        for (Draft draft : drafts) {
            Chapter chapter = draft.finish(fallbackTitle);
            if (!chapter.blocks().isEmpty()) {
                chapters.add(chapter);
            }
        }
        return chapters;
    }

    // ---- .epub -------------------------------------------------------------------------------

    private static List<Chapter> epub(Map<String, byte[]> files) {
        byte[] container = files.get("META-INF/container.xml");
        if (container == null) {
            throw UserFacingException.badRequest("Це не схоже на .epub: немає META-INF/container.xml.");
        }
        Element rootfile = xml(container).selectFirst("rootfile[full-path]");
        String opfPath = rootfile == null ? null : rootfile.attr("full-path");
        byte[] opfBytes = opfPath == null ? null : files.get(opfPath);
        if (opfBytes == null) {
            throw UserFacingException.badRequest("Не вдалося прочитати зміст .epub.");
        }
        Document opf = xml(opfBytes);
        Map<String, String> manifest = new HashMap<>();
        java.util.Set<String> covers = new java.util.HashSet<>();
        for (Element item : opf.select("manifest > item, item")) {
            manifest.put(item.attr("id"), item.attr("href"));
            if (item.attr("properties").contains("cover-image")) {
                covers.add(resolve(opfPath, item.attr("href")));
            }
        }
        Element coverMeta = opf.selectFirst("meta[name=cover]");
        if (coverMeta != null && manifest.containsKey(coverMeta.attr("content"))) {
            covers.add(resolve(opfPath, manifest.get(coverMeta.attr("content"))));
        }
        List<Draft> drafts = new ArrayList<>();
        // Pictures on pages of their own (an illustration between scenes) belong to the chapter
        // they stand in; those before the first chapter (colour plates) open it.
        List<String> waiting = new ArrayList<>();
        for (Element ref : opf.select("spine > itemref, itemref")) {
            String href = manifest.get(ref.attr("idref"));
            if (href == null) {
                continue;
            }
            String path = resolve(opfPath, href);
            byte[] page = files.get(path);
            if (page == null) {
                continue;
            }
            Draft draft = page(Jsoup.parse(new String(page, StandardCharsets.UTF_8)), path, files, covers);
            if (draft.hasText()) {
                waiting.forEach(draft::prependPicture);
                waiting.clear();
                drafts.add(draft);
            } else if (!drafts.isEmpty()) {
                draft.picturePaths().forEach(drafts.getLast()::addPicture);
            } else {
                waiting.addAll(draft.picturePaths());
            }
        }
        return drafts.stream().map(draft -> draft.finish("Глава")).toList();
    }

    /** One document of a book: its text and pictures in order; the cover and a contents page have no text. */
    private static Draft page(Document page, String path, Map<String, byte[]> files, java.util.Set<String> covers) {
        page.select("rt, rp, script, style, nav").remove();
        Element body = page.body();
        Element heading = body.selectFirst("h1, h2, h3");
        String title = heading != null ? heading.text().strip() : page.title().strip();
        if (heading != null) {
            heading.remove();
        }
        Draft draft = new Draft(title.isEmpty() ? null : title(title));
        List<Element> parts = body.select("p, hr, img, image, div:not(:has(p, div))");
        if (parts.stream().noneMatch(part -> part.tagName().equals("p") || part.tagName().equals("div"))) {
            for (String line : body.wholeText().split("\n")) {
                String text = line.replaceAll("\\s+", " ").strip();
                if (!text.isEmpty()) {
                    draft.add(text);
                }
            }
        }
        for (Element part : parts) {
            switch (part.tagName()) {
                case "hr" -> draft.add("◇◇◇");
                case "img", "image" -> {
                    String src = part.hasAttr("src") ? part.attr("src") : part.hasAttr("xlink:href") ? part.attr("xlink:href") : part.attr("href");
                    String picture = src.isBlank() ? null : resolve(path, src);
                    if (picture != null && files.containsKey(picture) && picture(picture) && !covers.contains(picture)) {
                        draft.addPicture(picture);
                    }
                }
                default -> {
                    // Text only: a picture inside the paragraph comes as its own part.
                    String text = part.wholeText().replaceAll("\\s+", " ").strip();
                    if (!text.isEmpty()) {
                        draft.add(text);
                    }
                }
            }
        }
        return draft;
    }

    private static boolean picture(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    private static Map<String, byte[]> unzip(byte[] content) {
        Map<String, byte[]> files = new HashMap<>();
        long unpacked = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (files.size() >= EPUB_MAX_ENTRIES) {
                    throw UserFacingException.badRequest("У .epub забагато файлів.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                boolean wanted = name.endsWith(".xml") || name.endsWith(".opf") || name.endsWith(".xhtml")
                        || name.endsWith(".html") || name.endsWith(".htm") || picture(name);
                byte[] bytes = read(zip, EPUB_MAX_UNPACKED - unpacked);
                unpacked += bytes.length;
                if (wanted) {
                    files.put(name, bytes);
                }
            }
        } catch (IOException error) {
            throw UserFacingException.badRequest("Не вдалося відкрити .epub: файл пошкоджений.");
        }
        if (files.isEmpty()) {
            throw UserFacingException.badRequest("Це не схоже на .epub.");
        }
        return files;
    }

    private static byte[] read(InputStream in, long budget) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = in.read(buffer)) > 0) {
            if (out.size() + read > budget) {
                throw UserFacingException.badRequest("Текст у .epub завеликий.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Document xml(byte[] bytes) {
        return Jsoup.parse(new String(bytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
    }

    /** A link inside the book, relative to the file it is in, as a path inside the zip. */
    private static String resolve(String from, String href) {
        String decoded = java.net.URLDecoder.decode(href.replace("+", "%2B").replaceAll("#.*$", ""), StandardCharsets.UTF_8);
        int slash = from.lastIndexOf('/');
        String base = slash < 0 ? "" : from.substring(0, slash + 1);
        try {
            String path = URI.create("x:/" + (base + decoded).replace(" ", "%20")).normalize().getPath();
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (IllegalArgumentException odd) {
            return base + decoded;
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    /** UTF-8 as most files are; Japanese .txt from Windows is often Shift_JIS. */
    private static String decode(byte[] content) {
        String utf8 = new String(content, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        try {
            return new String(content, java.nio.charset.Charset.forName("windows-31j"));
        } catch (RuntimeException unsupported) {
            return utf8;
        }
    }

    private static String title(String line) {
        String plain = line.strip();
        return plain.length() > TITLE_MAX ? plain.substring(0, TITLE_MAX) : plain;
    }

    private static String baseName(String fileName) {
        String name = fileName.replaceAll(".*[/\\\\]", "");
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Characters without spaces: what a chapter costs to translate. */
    static int chars(List<Block> blocks) {
        return blocks.stream().mapToInt(block -> block.text().replaceAll("\\s", "").length()).sum();
    }

    private static final class Draft {
        private final String title;
        private final List<Block> blocks = new ArrayList<>();

        Draft(String title) {
            this.title = title;
        }

        void add(String line) {
            String id = "b" + (blocks.size() + 1);
            blocks.add(SEPARATOR.matcher(line).matches() ? Block.separator(id) : Block.paragraph(id, List.of(Span.plain(line))));
        }

        void addPicture(String path) {
            blocks.add(new Block("b" + (blocks.size() + 1), "image", List.of(), null, EPUB_PICTURE + path));
        }

        void prependPicture(String path) {
            blocks.addFirst(new Block("p", "image", List.of(), null, EPUB_PICTURE + path));
        }

        boolean hasText() {
            return blocks.stream().anyMatch(block -> block.type().equals("paragraph"));
        }

        List<String> picturePaths() {
            return blocks.stream().filter(block -> block.type().equals("image"))
                    .map(block -> block.sourceUrl().substring(EPUB_PICTURE.length())).toList();
        }

        Chapter finish(String fallbackTitle) {
            List<Block> kept = blocks;
            String name = title;
            if (name == null) {
                // No heading: a short first line is the title, as in most pasted chapters.
                Block first = blocks.isEmpty() ? null : blocks.getFirst();
                if (first != null && first.type().equals("paragraph") && first.text().length() <= 60 && blocks.size() > 1) {
                    name = title(first.text());
                    kept = renumber(blocks.subList(1, blocks.size()));
                } else {
                    name = fallbackTitle;
                }
            }
            while (!kept.isEmpty() && kept.getLast().type().equals("separator")) {
                kept = kept.subList(0, kept.size() - 1);
            }
            kept = renumber(kept);
            return new Chapter(name, List.copyOf(kept), chars(kept));
        }
    }

    private static List<Block> renumber(List<Block> blocks) {
        List<Block> out = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            out.add(new Block("b" + (i + 1), block.type(), block.content(), block.imageId(), block.sourceUrl()));
        }
        return out;
    }

    /** Pasted text. */
    public static List<Chapter> parseText(String text) {
        return parse(null, text.getBytes(StandardCharsets.UTF_8)).chapters();
    }
}
