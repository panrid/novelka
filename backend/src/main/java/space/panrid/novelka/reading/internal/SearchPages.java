package space.panrid.novelka.reading.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.REVISION;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.platform.SiteProperties;
import space.panrid.novelka.reading.internal.ReadingQueries.EditionRow;
import space.panrid.novelka.reading.internal.ReadingQueries.NovelRow;
import tools.jackson.databind.json.JsonMapper;

/**
 * What search engines and link previews get (етап 16). The app draws pages in the browser
 * from the API, so a novel's or a chapter's page is sent with its title, description, cover and
 * text already in the HTML; the same React app then takes over for people. Adult translations
 * stay out of search; hidden ones are not shown at all.
 */
@RestController
class SearchPages {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final Pattern ROOT = Pattern.compile("<div id=\"root\">.*?</div>", Pattern.DOTALL);
    private static final int DESCRIPTION_CHARS = 160;
    private static final int SITEMAP_LIMIT = 45_000;
    private static final String SITE = "Новелка";

    private final ReadingQueries queries;
    private final DSLContext db;
    private final JsonMapper json;
    private final String base;
    private volatile String shell;

    SearchPages(ReadingQueries queries, DSLContext db, JsonMapper json, SiteProperties site) {
        this.queries = queries;
        this.db = db;
        this.json = json;
        this.base = site.publicUrl();
    }

    // ---- pages ---------------------------------------------------------------------------------

    @GetMapping("/")
    ResponseEntity<String> home() {
        return page(new Head("Новелка — новели й ранобе українською",
                "Читайте японські, корейські й китайські новели та ранобе українською: переклади команд і оригінальні твори.",
                "/", null, true, null), "<h1>Новелка</h1><p>Новели й ранобе українською.</p>", HttpStatus.OK);
    }

    @GetMapping("/catalog")
    ResponseEntity<String> catalog() {
        return page(new Head("Каталог новел — " + SITE, "Усі новели й ранобе українською: пошук за назвою, автором і тегами.",
                "/catalog", null, true, null), "<h1>Каталог новел</h1>", HttpStatus.OK);
    }

    @GetMapping("/proposals")
    ResponseEntity<String> proposals() {
        return page(new Head("Що перекласти — " + SITE, "Пропонуйте новели для перекладу українською й голосуйте за ті, які хочете читати.",
                "/proposals", null, true, null), "<h1>Що перекласти</h1>", HttpStatus.OK);
    }

    /** An address the novel had before: search engines and old links move to the new one for good. */
    private Optional<ResponseEntity<String>> moved(String slug, String rest, String team) {
        return queries.novel(slug).filter(novel -> !novel.slug().equals(slug)).map(novel -> ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header("Location", "/n/" + novel.slug() + rest + (team == null || team.isBlank() ? "" : "?t=" + encode(team)))
                .<String>body(null));
    }

    @GetMapping("/n/{slug}")
    ResponseEntity<String> novel(@PathVariable String slug, @RequestParam(required = false) String t) {
        Optional<ResponseEntity<String>> moved = moved(slug, "", t);
        if (moved.isPresent()) {
            return moved.get();
        }
        Optional<Found> found = find(slug, t);
        if (found.isEmpty()) {
            return page(new Head(SITE, "", "/n/" + slug, null, false, null), "", HttpStatus.NOT_FOUND);
        }
        Found it = found.get();
        if (it.edition().adult()) {
            return page(new Head(it.title() + " — " + SITE, "", it.path(), null, false, null), "", HttpStatus.OK);
        }
        String about = text(it.description());
        String cover = cover(it);
        StringBuilder body = new StringBuilder("<article class=\"prerendered\"><h1>").append(escape(it.title())).append("</h1>");
        if (!it.novel().author().isBlank()) {
            body.append("<p>").append(escape(it.novel().author())).append("</p>");
        }
        body.append(paragraphs(it.description()));
        body.append("<h2>Глави</h2><ol>");
        for (Views.ChapterRow row : queries.chapters(it.edition().id(), false, 1, 500).items()) {
            body.append("<li><a href=\"").append(escape(chapterPath(it, row.number()))).append("\">")
                    .append(escape(heading(row.number(), row.label(), row.title()))).append("</a></li>");
        }
        body.append("</ol></article>");
        String ld = json.writeValueAsString(java.util.Map.of(
                "@context", "https://schema.org", "@type", "Book", "name", it.title(),
                "author", java.util.Map.of("@type", "Person", "name", it.novel().author().isBlank() ? SITE : it.novel().author()),
                "inLanguage", "uk", "url", base + it.path(), "image", cover == null ? base + "/favicon.svg" : cover,
                "description", cut(about, 500)));
        return page(new Head(it.title() + " — читати українською | " + SITE, cut(about, DESCRIPTION_CHARS), it.path(), cover, true, ld),
                body.toString(), HttpStatus.OK);
    }

    @GetMapping("/n/{slug}/{number}")
    ResponseEntity<String> chapter(@PathVariable String slug, @PathVariable int number, @RequestParam(required = false) String t) {
        Optional<ResponseEntity<String>> moved = moved(slug, "/" + number, t);
        if (moved.isPresent()) {
            return moved.get();
        }
        Optional<Found> found = find(slug, t);
        if (found.isEmpty()) {
            return page(new Head(SITE, "", "/n/" + slug + "/" + number, null, false, null), "", HttpStatus.NOT_FOUND);
        }
        Found it = found.get();
        String path = chapterPath(it, number);
        Optional<ReadingQueries.ChapterText> chapter = queries.chapter(it.edition().id(), number);
        if (chapter.isEmpty()) {
            return page(new Head(it.title() + " — " + SITE, "", path, null, false, null), "", HttpStatus.NOT_FOUND);
        }
        if (it.edition().adult()) {
            return page(new Head(it.title() + " — " + SITE, "", path, null, false, null), "", HttpStatus.OK);
        }
        ReadingQueries.ChapterText text = chapter.get();
        String heading = heading(number, text.label(), text.title());
        List<Views.ReaderBlock> blocks = queries.readerBlocks(text.blocks());
        String plain = blocks.stream().map(SearchPages::blockText).filter(line -> !line.isBlank()).collect(Collectors.joining(" "));
        StringBuilder body = new StringBuilder("<article class=\"prerendered\"><p><a href=\"").append(escape(it.path())).append("\">")
                .append(escape(it.title())).append("</a></p><h1>").append(escape(heading)).append("</h1>");
        for (Views.ReaderBlock block : blocks) {
            String line = blockText(block);
            if (!line.isBlank()) {
                body.append("<p>").append(escape(line)).append("</p>");
            }
        }
        Integer previous = queries.neighbour(it.edition().id(), number, false);
        Integer next = queries.neighbour(it.edition().id(), number, true);
        body.append("<nav>");
        if (previous != null) {
            body.append("<a rel=\"prev\" href=\"").append(escape(chapterPath(it, previous))).append("\">Попередня глава</a> ");
        }
        if (next != null) {
            body.append("<a rel=\"next\" href=\"").append(escape(chapterPath(it, next))).append("\">Наступна глава</a>");
        }
        body.append("</nav></article>");
        String ld = json.writeValueAsString(java.util.Map.of(
                "@context", "https://schema.org", "@type", "Chapter", "name", heading, "inLanguage", "uk", "url", base + path,
                "isPartOf", java.util.Map.of("@type", "Book", "name", it.title(), "url", base + it.path())));
        return page(new Head(heading + " — " + it.title() + " | " + SITE, cut(plain, DESCRIPTION_CHARS), path, cover(it), true, ld),
                body.toString(), HttpStatus.OK);
    }

    // ---- robots and sitemap --------------------------------------------------------------------

    @GetMapping("/robots.txt")
    ResponseEntity<String> robots() {
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8)).body("""
                # Новелка: novels and chapters are for everyone; the rest is personal or for the teams.
                User-agent: *
                Disallow: /api/
                Disallow: /studio
                Disallow: /admin
                Disallow: /me
                Disallow: /inbox
                Disallow: /library
                Disallow: /login
                Disallow: /register
                Disallow: /reset

                Sitemap: %s/sitemap.xml
                """.formatted(base));
    }

    @GetMapping("/sitemap.xml")
    ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(xml, "/", null);
        url(xml, "/catalog", null);
        url(xml, "/proposals", null);
        int[] count = {3};
        var novels = db.select(NOVEL.ID, NOVEL.SLUG).from(NOVEL).orderBy(NOVEL.ID).fetch();
        for (var novel : novels) {
            List<EditionRow> editions = visible(novel.value1());
            for (EditionRow edition : editions) {
                if (edition.adult() || count[0] >= SITEMAP_LIMIT) {
                    continue;
                }
                String team = edition == editions.getFirst() ? "" : "?t=" + encode(edition.teamHandle());
                url(xml, "/n/" + novel.value2() + team, edition.lastPublishedAt());
                count[0]++;
                db.select(CHAPTER.NUMBER, REVISION.CREATED_AT).from(CHAPTER)
                        .join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                        .where(CHAPTER.EDITION_ID.eq(edition.id())).orderBy(CHAPTER.NUMBER)
                        .forEach(row -> {
                            if (count[0] < SITEMAP_LIMIT) {
                                url(xml, "/n/" + novel.value2() + "/" + row.value1() + team, row.value2());
                                count[0]++;
                            }
                        });
            }
        }
        return ResponseEntity.ok().contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .body(xml.append("</urlset>\n").toString());
    }

    private void url(StringBuilder xml, String path, OffsetDateTime changed) {
        xml.append("  <url><loc>").append(escape(base + path)).append("</loc>");
        if (changed != null) {
            xml.append("<lastmod>").append(changed.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .append("</lastmod>");
        }
        xml.append("</url>\n");
    }

    // ---- the translation a link points at ------------------------------------------------------

    /** @param path the page's own address: with ?t= only for a translation that is not the main one */
    private record Found(NovelRow novel, EditionRow edition, String title, String path, List<EditionRow> editions) {

        org.jooq.JSONB description() {
            return edition.description() != null ? edition.description() : novel.description();
        }
    }

    private Optional<Found> find(String slug, String team) {
        Optional<NovelRow> novel = queries.novel(slug);
        if (novel.isEmpty()) {
            return Optional.empty();
        }
        List<EditionRow> editions = visible(novel.get().id());
        if (editions.isEmpty()) {
            return Optional.empty();
        }
        EditionRow edition = team == null || team.isBlank() ? editions.getFirst()
                : editions.stream().filter(e -> e.teamHandle().equalsIgnoreCase(team)).findFirst().orElse(null);
        if (edition == null) {
            return Optional.empty();
        }
        String path = "/n/" + novel.get().slug() + (edition == editions.getFirst() ? "" : "?t=" + encode(edition.teamHandle()));
        return Optional.of(new Found(novel.get(), edition, edition.title() != null ? edition.title() : novel.get().title(), path, editions));
    }

    /** Translations readers can open: not hidden, with chapters; the most popular first. */
    private List<EditionRow> visible(long novelId) {
        return queries.editions(novelId).stream().filter(edition -> !edition.hidden() && edition.chapterCount() > 0).toList();
    }

    private String chapterPath(Found it, int number) {
        String team = it.edition() == it.editions().getFirst() ? "" : "?t=" + encode(it.edition().teamHandle());
        return "/n/" + it.novel().slug() + "/" + number + team;
    }

    private String cover(Found it) {
        String url = queries.summaries(List.of(it.edition())).getFirst().coverUrl();
        return url == null ? null : url.startsWith("http") ? url : base + url;
    }

    // ---- HTML ----------------------------------------------------------------------------------

    /** @param index false: the page is shown to people but kept out of search */
    private record Head(String title, String description, String path, String image, boolean index, String jsonLd) {
    }

    private ResponseEntity<String> page(Head head, String body, HttpStatus status) {
        StringBuilder meta = new StringBuilder("<title>").append(escape(head.title())).append("</title>");
        if (!head.description().isBlank()) {
            meta.append("<meta name=\"description\" content=\"").append(escape(head.description())).append("\">");
        }
        meta.append("<link rel=\"canonical\" href=\"").append(escape(base + head.path())).append("\">");
        if (!head.index()) {
            meta.append("<meta name=\"robots\" content=\"noindex\">");
        }
        meta.append("<meta property=\"og:site_name\" content=\"").append(SITE).append("\">")
                .append("<meta property=\"og:locale\" content=\"uk_UA\">")
                .append("<meta property=\"og:title\" content=\"").append(escape(head.title())).append("\">")
                .append("<meta property=\"og:url\" content=\"").append(escape(base + head.path())).append("\">")
                .append("<meta property=\"og:type\" content=\"").append(head.jsonLd() != null ? "book" : "website").append("\">");
        if (!head.description().isBlank()) {
            meta.append("<meta property=\"og:description\" content=\"").append(escape(head.description())).append("\">");
        }
        if (head.image() != null) {
            meta.append("<meta property=\"og:image\" content=\"").append(escape(head.image())).append("\">")
                    .append("<meta name=\"twitter:card\" content=\"summary_large_image\">");
        }
        if (head.jsonLd() != null) {
            meta.append("<script type=\"application/ld+json\">").append(head.jsonLd().replace("</", "<\\/")).append("</script>");
        }
        String html = shell();
        html = html.contains("<title>Новелка</title>") ? html.replace("<title>Новелка</title>", meta.toString())
                : html.replace("</head>", meta + "</head>");
        Matcher root = ROOT.matcher(html);
        html = root.find() ? root.replaceFirst(Matcher.quoteReplacement("<div id=\"root\">" + body + "</div>")) : html;
        return ResponseEntity.status(status).contentType(HTML).body(html);
    }

    /** The built index.html; read once, it never changes while the server runs. */
    private String shell() {
        String loaded = shell;
        if (loaded == null) {
            try (InputStream in = new ClassPathResource("static/index.html").getInputStream()) {
                loaded = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException missing) {
                loaded = "<!doctype html><html lang=\"uk\"><head><meta charset=\"utf-8\"><title>Новелка</title></head>"
                        + "<body><div id=\"root\"></div></body></html>";
            }
            shell = loaded;
        }
        return loaded;
    }

    private String paragraphs(org.jooq.JSONB stored) {
        StringBuilder out = new StringBuilder();
        for (Views.ReaderBlock block : queries.readerBlocks(stored)) {
            String line = blockText(block);
            if (!line.isBlank()) {
                out.append("<p>").append(escape(line)).append("</p>");
            }
        }
        return out.toString();
    }

    private String text(org.jooq.JSONB stored) {
        return queries.readerBlocks(stored).stream().map(SearchPages::blockText).filter(line -> !line.isBlank())
                .collect(Collectors.joining(" "));
    }

    private static String blockText(Views.ReaderBlock block) {
        return block.content() == null ? "" : block.content().stream().map(space.panrid.novelka.platform.text.Span::text)
                .collect(Collectors.joining()).strip();
    }

    /** «31.1. Ніч», «Пролог», «Глава 12»: as the reader shows it. */
    static String heading(int number, String label, String title) {
        String shown = label == null ? String.valueOf(number) : label;
        if (title == null || title.isBlank()) {
            return shown.isEmpty() ? "Без назви" : "Глава " + shown;
        }
        return shown.isEmpty() ? title : shown + ". " + title;
    }

    static String cut(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").strip();
        if (clean.length() <= max) {
            return clean;
        }
        int space = clean.lastIndexOf(' ', max - 1);
        return clean.substring(0, space > max / 2 ? space : max - 1) + "…";
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
