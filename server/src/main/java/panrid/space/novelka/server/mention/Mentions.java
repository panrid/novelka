package panrid.space.novelka.server.mention;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mentions in comments and chat. Authors type {@code @nick}; the text is stored with {@code <@account-id>} tokens,
 * so a mention keeps pointing at the person after a nickname change. Readers get a map of ids to current nicknames.
 */
public final class Mentions {
    public static final int LIMIT = 10;
    private static final Pattern NICK = Pattern.compile("(?<![A-Za-z0-9_@-])@([A-Za-z0-9][A-Za-z0-9_-]{2,39})");
    private static final Pattern TOKEN = Pattern.compile("<@([0-9a-f-]{36})>");

    private Mentions() { }

    /** Replaces {@code @nick} of existing accounts with tokens; unknown nicknames stay plain text. */
    public static String encode(JdbcSession jdbc, String body) throws Exception {
        var nicks = new LinkedHashSet<String>();
        Matcher found = NICK.matcher(body);
        while (found.find()) nicks.add(found.group(1).toLowerCase(Locale.ROOT));
        if (nicks.isEmpty()) return body;
        var ids = new HashMap<String, String>();
        for (var row : jdbc.rows("SELECT id,username FROM accounts WHERE username IN (SELECT jsonb_array_elements_text(?::jsonb))",
                Json.write(nicks))) ids.put((String) row.get("username"), (String) row.get("id"));
        Matcher matcher = NICK.matcher(body);
        var result = new StringBuilder();
        while (matcher.find()) {
            String id = ids.get(matcher.group(1).toLowerCase(Locale.ROOT));
            matcher.appendReplacement(result, Matcher.quoteReplacement(id == null ? matcher.group() : "<@" + id + ">"));
        }
        matcher.appendTail(result);
        if (mentioned(result.toString()).size() > LIMIT) throw new IllegalArgumentException("Можна згадати до " + LIMIT + " людей в одному повідомленні.");
        return result.toString();
    }

    /** Account ids mentioned in stored text, in order of first appearance. */
    public static Set<String> mentioned(String body) {
        var ids = new LinkedHashSet<String>();
        if (body == null) return ids;
        Matcher matcher = TOKEN.matcher(body);
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    /** Current nicknames of every account mentioned in the given texts, for rendering. */
    public static Map<String, String> names(JdbcSession jdbc, Collection<String> bodies) throws Exception {
        var ids = new LinkedHashSet<String>();
        for (var body : bodies) ids.addAll(mentioned(body));
        var names = new HashMap<String, String>();
        if (ids.isEmpty()) return names;
        for (var row : jdbc.rows("SELECT id,username FROM accounts WHERE id IN (SELECT jsonb_array_elements_text(?::jsonb))", Json.write(ids)))
            names.put((String) row.get("id"), (String) row.get("username"));
        return names;
    }
}
