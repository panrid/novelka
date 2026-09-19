package panrid.space.novelka.core;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Chapter;
import panrid.space.novelka.core.model.Novel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Syosetu implements NovelSource {
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public static String code(String url) {
        URI u = URI.create(url);
        if (!"https".equals(u.getScheme())
                || !"ncode.syosetu.com".equalsIgnoreCase(u.getHost())
                || u.getUserInfo() != null
                || u.getPort() != -1
                || !u.getPath().matches("/n[0-9]+[a-zA-Z]+(?:/[0-9]+)?/?"))
            throw new IllegalArgumentException("Use https://ncode.syosetu.com/n…/ URL");
        return u.getPath().split("/")[1].toLowerCase(Locale.ROOT);
    }

    private String get(String url) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            Thread.sleep(attempt == 0 ? 1000 : 2000L * attempt);
            var r =
                    http.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .timeout(Duration.ofSeconds(60))
                                    .header("User-Agent", "Novelka/0.1 personal translation client")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) return r.body();
            if (r.statusCode() != 429 && r.statusCode() < 500)
                throw new IllegalStateException("Syosetu HTTP " + r.statusCode());
        }
        throw new IllegalStateException("Syosetu temporarily unavailable");
    }

    public Novel inspect(String url) throws Exception {
        String id = code(url);
        var data = Json.read(get("https://api.syosetu.com/novelapi/api/?out=json&ncode=" + id));
        if (!data.isArray() || data.size() < 2)
            throw new IllegalArgumentException("Novel not found in Syosetu API");
        var n = data.get(1);
        return new Novel(
                id,
                n.path("title").asText(),
                n.path("writer").asText(),
                "https://ncode.syosetu.com/" + id + "/",
                n.path("general_all_no").asInt(1),
                n.path("novel_type").asInt() == 2);
    }

    public Chapter fetch(Novel n, int number) throws Exception {
        if (number < 1 || number > n.chapterCount())
            throw new IllegalArgumentException("Chapter outside novel range");
        String url = n.url() + (n.shortStory() ? "" : number + "/");
        return parse(number, url, get(url));
    }

    public static Chapter parse(int number, String url, String html) {
        Document doc = Jsoup.parse(html, url);
        Element title = doc.selectFirst(".p-novel__title, .novel_subtitle, .novel_title");
        Element body =
                doc.selectFirst(
                        ".p-novel__text:not(.p-novel__text--preface):not(.p-novel__text--afterword),"
                                + " #novel_honbun");
        if (title == null || body == null)
            throw new IllegalArgumentException("Unsupported Syosetu page: title/body missing");
        var blocks = new ArrayList<Block>();
        blocks.add(new Block("title", "heading", title.text()));
        add(doc.selectFirst(".p-novel__text--preface, #novel_p"), "preface", blocks);
        add(body, "paragraph", blocks);
        add(doc.selectFirst(".p-novel__text--afterword, #novel_a"), "afterword", blocks);
        if (blocks.stream().noneMatch(b -> b.kind().equals("paragraph")))
            throw new IllegalArgumentException("Empty chapter");
        return new Chapter(number, url, title.text(), List.copyOf(blocks), html);
    }

    private static void add(Element element, String kind, List<Block> blocks) {
        if (element == null) return;
        var paragraphs = element.select("p");
        if (paragraphs.isEmpty()) throw new IllegalArgumentException("Unsupported text layout");
        int count = 0;
        for (Element p : paragraphs) {
            Element copy = p.clone();
            for (Element ruby : copy.select("ruby")) {
                String reading = ruby.select("rt").text();
                ruby.select("rt,rp").remove();
                ruby.appendText("〔" + reading + "〕");
            }
            copy.select("br").append("\n");
            String text = copy.wholeText().strip();
            if (!text.isBlank())
                blocks.add(
                        new Block(
                                kind + "-" + (++count),
                                text.matches("[＊*◇◆○●ー—\\s]{3,}") ? "separator" : kind,
                                text));
        }
    }
}
