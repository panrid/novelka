package space.panrid.novelka.community;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * «@мавка» and «$panrid» in what people write. Stored as {@code <@u:12>} and {@code <$t:3>},
 * so a mention follows a person or team through a rename; shown with today's names.
 */
@Component
public class Mentions {

    /** A name after @ or $, not glued to a word before it (so e-mails stay e-mails). */
    private static final Pattern TYPED = Pattern.compile("(?<![\\p{L}\\d_@$-])([@$])([\\p{L}\\d][\\p{L}\\d_-]{2,29})");
    private static final Pattern STORED = Pattern.compile("<([@$])([ut]):(\\d{1,18})>");

    private final DSLContext db;

    Mentions(DSLContext db) {
        this.db = db;
    }

    /** Text with mentions made permanent, and who was mentioned. */
    public record Encoded(String body, List<Long> accounts, List<Long> teams) {
    }

    public Encoded encode(String text) {
        Set<String> nicks = new LinkedHashSet<>();
        Set<String> handles = new LinkedHashSet<>();
        Matcher found = TYPED.matcher(text);
        while (found.find()) {
            (found.group(1).equals("@") ? nicks : handles).add(found.group(2).toLowerCase(Locale.ROOT));
        }
        Map<String, Long> people = nicks.isEmpty() ? Map.of()
                : db.select(ACCOUNT.NICK_KEY, ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.in(nicks)).fetchMap(ACCOUNT.NICK_KEY, ACCOUNT.ID);
        Map<String, Long> teams = handles.isEmpty() ? Map.of()
                : db.select(TEAM.HANDLE_KEY, TEAM.ID).from(TEAM).where(TEAM.HANDLE_KEY.in(handles)).fetchMap(TEAM.HANDLE_KEY, TEAM.ID);
        Set<Long> accountIds = new LinkedHashSet<>();
        Set<Long> teamIds = new LinkedHashSet<>();
        StringBuilder out = new StringBuilder();
        Matcher matcher = TYPED.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean person = matcher.group(1).equals("@");
            Long id = person ? people.get(key) : teams.get(key);
            if (id == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            (person ? accountIds : teamIds).add(id);
            matcher.appendReplacement(out, person ? "<@u:" + id + ">" : "<\\$t:" + id + ">");
        }
        matcher.appendTail(out);
        return new Encoded(out.toString(), List.copyOf(accountIds), List.copyOf(teamIds));
    }

    /** Stored texts with today's names: {@code <@u:12>} → «@мавка». */
    public List<String> render(Collection<String> bodies) {
        Set<Long> accountIds = new LinkedHashSet<>();
        Set<Long> teamIds = new LinkedHashSet<>();
        for (String body : bodies) {
            Matcher matcher = STORED.matcher(body == null ? "" : body);
            while (matcher.find()) {
                (matcher.group(2).equals("u") ? accountIds : teamIds).add(Long.parseLong(matcher.group(3)));
            }
        }
        Map<Long, String> nicks = accountIds.isEmpty() ? Map.of()
                : db.select(ACCOUNT.ID, ACCOUNT.NICK).from(ACCOUNT).where(ACCOUNT.ID.in(accountIds)).fetchMap(ACCOUNT.ID, ACCOUNT.NICK);
        Map<Long, String> handles = teamIds.isEmpty() ? Map.of()
                : db.select(TEAM.ID, TEAM.HANDLE).from(TEAM).where(TEAM.ID.in(teamIds)).fetchMap(TEAM.ID, TEAM.HANDLE);
        List<String> out = new ArrayList<>();
        for (String body : bodies) {
            out.add(body == null ? null : STORED.matcher(body).replaceAll(match -> {
                long id = Long.parseLong(match.group(3));
                String name = match.group(2).equals("u") ? nicks.get(id) : handles.get(id);
                return Matcher.quoteReplacement(match.group(1) + (name == null ? "?" : name));
            }));
        }
        return out;
    }

    public String render(String body) {
        return render(List.of(body)).getFirst();
    }

    /** A short plain excerpt for a notification: names shown, markers dropped. */
    public String excerpt(String body, int length) {
        String plain = render(body).replaceAll("\\|\\||\\*\\*|__|\\+\\+|~~|(?m)^> ?", "").replaceAll("\\s+", " ").strip();
        return plain.length() <= length ? plain : plain.substring(0, length - 1) + "…";
    }
}
