package space.panrid.novelka.source.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Turns a Syosetu chapter page into blocks: preface, main text and afterword, one block per
 * non-empty line. Ruby readings stay in the text as {@code 漢字《かんじ》} so the model sees them;
 * pictures become image links for the media module to copy.
 */
final class SyosetuPages {

    /** A line of only such symbols is a scene break. */
    private static final Pattern SEPARATOR = Pattern.compile("[\\s\\u3000◇◆□■○●☆★＊*※―－ー─━~〜・…]{3,}");

    private SyosetuPages() {
    }

    static SourceProvider.Page chapter(String html) {
        Document page = Jsoup.parse(html, "https://ncode.syosetu.com/");
        Element heading = page.selectFirst(".p-novel__title");
        String title = heading == null ? "" : heading.text().strip();
        List<Block> blocks = new ArrayList<>();
        for (Element part : page.select(".js-novel-text")) {
            String type = part.hasClass("p-novel__text--preface") ? "preface"
                    : part.hasClass("p-novel__text--afterword") ? "afterword" : "paragraph";
            for (Element line : part.select("p")) {
                String id = "s" + (blocks.size() + 1);
                Element picture = line.selectFirst("img[src]");
                if (picture != null) {
                    String src = picture.absUrl("src").replaceFirst("^http:", "https:");
                    if (!src.isEmpty()) {
                        blocks.add(Block.imageLink(id, src));
                    }
                    continue;
                }
                String text = text(line).strip().replaceAll("^\\u3000+|\\u3000+$", "");
                if (text.isEmpty()) {
                    continue;
                }
                blocks.add(SEPARATOR.matcher(text).matches() && "paragraph".equals(type)
                        ? Block.separator(id)
                        : new Block(id, type, List.of(Span.plain(text)), null, null));
            }
        }
        if (blocks.stream().noneMatch(block -> !block.text().isEmpty())) {
            throw UserFacingException.badGateway("На сторінці глави Syosetu не знайшлося тексту. Можливо, сайт змінив розмітку.");
        }
        return new SourceProvider.Page(title, blocks);
    }

    private static String text(Node node) {
        StringBuilder out = new StringBuilder();
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode plain) {
                out.append(plain.getWholeText());
            } else if (child instanceof Element element) {
                switch (element.normalName()) {
                    case "ruby" -> out.append(ruby(element));
                    case "br" -> out.append(' ');
                    case "rt", "rp", "script", "style" -> { }
                    default -> out.append(text(element));
                }
            }
        }
        return out.toString();
    }

    private static String ruby(Element ruby) {
        StringBuilder base = new StringBuilder();
        StringBuilder reading = new StringBuilder();
        for (Node child : ruby.childNodes()) {
            if (child instanceof TextNode plain) {
                base.append(plain.getWholeText());
            } else if (child instanceof Element element) {
                switch (element.normalName()) {
                    case "rt" -> reading.append(element.text());
                    case "rp" -> { }
                    default -> base.append(element.text());
                }
            }
        }
        String baseText = base.toString().strip();
        String readingText = reading.toString().strip();
        return readingText.isEmpty() || readingText.equals(baseText) ? baseText : baseText + "《" + readingText + "》";
    }
}
