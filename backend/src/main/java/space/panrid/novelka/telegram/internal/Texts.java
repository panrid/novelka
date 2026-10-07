package space.panrid.novelka.telegram.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.SiteProperties;

/**
 * What the bot writes, in Telegram's HTML: the same words as the rows in «Сповіщення»
 * (NotificationsPage.tsx), with a link to the place on the site.
 */
@Component
class Texts {

    private final SiteProperties site;

    Texts(SiteProperties site) {
        this.site = site;
    }

    String notification(String kind, Map<String, Object> p) {
        String actor = text(p, "actorNick");
        String where = text(p, "novelTitle").isEmpty() ? "" : text(p, "novelTitle")
                + (p.containsKey("chapterLabel") || p.containsKey("chapterNumber")
                        ? " · глава " + (text(p, "chapterLabel").isEmpty() ? text(p, "chapterNumber") : text(p, "chapterLabel")) : "");
        String excerpt = text(p, "excerpt");
        String title;
        String path = chapterPath(p);
        switch (kind) {
            case "reply" -> title = actor + " відповідає на ваш коментар";
            case "mention" -> {
                boolean chat = "chat".equals(text(p, "where"));
                title = actor + (chat ? " згадує вас у чаті" : " згадує вас");
                if (chat) {
                    path = "/inbox/chat";
                }
            }
            case "team_mention" -> title = actor + " згадує команду $" + text(p, "teamHandle");
            case "new_chapters" -> {
                int first = number(p, "first");
                int last = number(p, "last");
                int count = last - first + 1;
                String firstLabel = text(p, "firstLabel").isEmpty() ? String.valueOf(first) : text(p, "firstLabel");
                String lastLabel = text(p, "lastLabel").isEmpty() ? String.valueOf(last) : text(p, "lastLabel");
                String chapters = count > 1
                        ? "Нові глави %s–%s · %d %s".formatted(firstLabel, lastLabel, count, plural(count, "глава", "глави", "глав"))
                        : "Нова глава " + firstLabel + (text(p, "chapterTitle").isEmpty() ? "" : ". " + text(p, "chapterTitle"));
                return message("📖 <b>" + escape(text(p, "novelTitle")) + "</b>\n" + escape(chapters),
                        "/n/" + text(p, "slug") + "/" + first + team(p, "?"));
            }
            case "suggestions_submitted" -> {
                int count = number(p, "count") == 0 ? 1 : number(p, "count");
                title = count == 1 ? actor + " пропонує правку"
                        : "%d %s".formatted(count, plural(count, "нова правка", "нові правки", "нових правок"));
                path = "/studio/" + text(p, "editionId") + "/suggestions";
            }
            case "suggestions_reviewed" ->
                    title = "Ваші правки перевірено: прийнято %s, відхилено %s".formatted(text(p, "accepted"), text(p, "rejected"));
            case "shahs_granted" -> {
                int shah = number(p, "shah");
                title = "Вам нараховано %d %s".formatted(shah, plural(shah, "шаг", "шаги", "шагів"));
                excerpt = text(p, "note");
                path = "/me/shahs";
            }
            case "takeover_request" -> {
                title = actor + " хоче продовжити ваш переклад «" + text(p, "title") + "»";
                excerpt = excerpt.isEmpty() ? "Команда " + text(p, "teamName") + ". Відповісти можна в Студії." : excerpt;
                path = "/studio/" + text(p, "editionId") + "/relay";
            }
            case "takeover_answered" -> title = Boolean.TRUE.equals(p.get("granted"))
                    ? "Вам дозволили продовжити «" + text(p, "title") + "»"
                    : "Власник поки не віддає «" + text(p, "title") + "»";
            case "achievement" -> {
                title = "Нове досягнення: «" + text(p, "title") + "»";
                path = "/u/" + text(p, "nick") + "#achievements";
            }
            case "proposal_taken" -> {
                title = "«" + text(p, "title") + "» взяли перекладати";
                excerpt = "Новела, за яку ви голосували. Перекладає $" + text(p, "teamHandle") + ".";
            }
            default -> title = "Нове сповіщення";
        }
        StringBuilder html = new StringBuilder("🔔 <b>").append(escape(title)).append("</b>");
        if (!where.isEmpty() && !kind.equals("shahs_granted") && !kind.equals("achievement")) {
            html.append("\n").append(escape(where));
        }
        if (!excerpt.isEmpty()) {
            html.append("\n«").append(escape(excerpt)).append("»");
        }
        return message(html.toString(), path == null ? "/inbox" : path);
    }

    String message(String kind, String title, String authorNick, String excerpt, long conversationId) {
        String head = switch (kind) {
            case "direct" -> "✉️ <b>" + escape(authorNick) + "</b>";
            case "team" -> "✉️ <b>" + escape(authorNick) + "</b> у чаті команди $" + escape(title);
            default -> "✉️ <b>" + escape(authorNick) + "</b> у «" + escape(title) + "»";
        };
        return message(head + "\n" + escape(shorten(excerpt, 300)), "/inbox/messages/" + conversationId);
    }

    String account(String text, String link) {
        return "🔐 " + escape(text) + (link == null ? "" : "\n" + escape(link));
    }

    String link(String path) {
        return site.link(path);
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String message(String html, String path) {
        return html + "\n<a href=\"" + escape(site.link(path)) + "\">Відкрити на Новелці</a>";
    }

    /** A comment or a chapter in the reader; null when the row is not about a chapter. */
    private static String chapterPath(Map<String, Object> p) {
        if (text(p, "slug").isEmpty() || text(p, "chapterNumber").isEmpty()) {
            return text(p, "slug").isEmpty() ? null : "/n/" + text(p, "slug") + team(p, "?");
        }
        String path = "/n/" + text(p, "slug") + "/" + text(p, "chapterNumber") + team(p, "?");
        return text(p, "commentId").isEmpty() ? path : path + "#c" + text(p, "commentId");
    }

    private static String team(Map<String, Object> p, String separator) {
        return text(p, "teamHandle").isEmpty() ? "" : separator + "t=" + URLEncoder.encode(text(p, "teamHandle"), StandardCharsets.UTF_8);
    }

    private static String text(Map<String, Object> p, String key) {
        Object value = p.get(key);
        return value == null ? "" : value.toString();
    }

    private static int number(Map<String, Object> p, String key) {
        return p.get(key) instanceof Number n ? n.intValue() : 0;
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    static String plural(int n, String one, String few, String many) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return one;
        }
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
    }
}
