package space.panrid.novelka.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SyosetuHttp;

/**
 * Syosetu without the network. Pages are made up here (no real novel text in the repo) but
 * follow the site's markup: title, preface, lines with ruby and empty lines, afterword.
 */
public class FakeSyosetu implements SyosetuHttp {

    public record Novel(String title, String author, String story, int chapters) {
    }

    private final Map<String, Novel> novels = new ConcurrentHashMap<>();
    public final List<String> requested = new CopyOnWriteArrayList<>();

    public void add(String code, Novel novel) {
        novels.put(code, novel);
    }

    @Override
    public String get(String url) {
        requested.add(url);
        if (url.startsWith("https://api.syosetu.com/")) {
            String code = url.substring(url.indexOf("ncode=") + 6);
            Novel novel = novels.get(code);
            if (novel == null) {
                return "[{\"allcount\":0}]";
            }
            return """
                    [{"allcount":1},{"title":"%s","writer":"%s","story":"%s","general_all_no":%d,"noveltype":1,"end":1}]"""
                    .formatted(novel.title(), novel.author(), novel.story(), novel.chapters());
        }
        String[] parts = url.replaceFirst("https://[a-z0-9]+\\.syosetu\\.com/", "").split("/");
        Novel novel = novels.get(parts[0]);
        if (novel == null || parts.length < 2) {
            throw UserFacingException.notFound("На Syosetu такої сторінки немає.");
        }
        return chapterPage(parts[0], Integer.parseInt(parts[1]));
    }

    public static String chapterPage(String code, int number) {
        return """
                <html><body><article class="p-novel">
                <h1 class="p-novel__title p-novel__title--rensai">第%1$d話　灯り</h1>
                <div class="js-novel-text p-novel__text p-novel__text--preface"><p id="Lp1">前書きです。</p></div>
                <div class="js-novel-text p-novel__text">
                <p id="L1">　<ruby>雪<rp>(</rp><rt>ユキ</rt><rp>)</rp></ruby>は灯台を見た。</p>
                <p id="L2"><br /></p>
                <p id="L3">「ユキ、行こう」と彼は言った。第%1$d話。</p>
                <p id="L4">◇◇◇</p>
                <p id="L5">夜が明けた。</p>
                </div>
                <div class="js-novel-text p-novel__text p-novel__text--afterword"><p id="La1">後書きです。%2$s</p></div>
                </article></body></html>""".formatted(number, code);
    }
}
