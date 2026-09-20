package panrid.space.novelka.core.service.glossary;

import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.core.support.Tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Dictionary {
    public static List<Entry> select(Glossary glossary, String text, int maxTokens) {
        var selected = new ArrayList<Entry>();
        int size = 0;
        for (var e : glossary.entries()) {
            boolean match =
                    text.contains(e.japanese())
                            || text.contains(e.key())
                            || (!e.ukrainian().isBlank() && text.contains(e.ukrainian()))
                            || e.aliases().stream().anyMatch(a -> !a.isBlank() && text.contains(a));
            if (match) {
                int len = Tokens.count(Json.write(e));
                if (size + len <= maxTokens) {
                    selected.add(e);
                    size += len;
                }
            }
        }
        String related =
                selected.stream().map(Entry::facts).collect(java.util.stream.Collectors.joining(" "));
        for (var e : glossary.entries())
            if (!selected.contains(e) && (related.contains(e.japanese()) || related.contains(e.key()))) {
                int len = Tokens.count(Json.write(e));
                if (size + len <= maxTokens) {
                    selected.add(e);
                    size += len;
                }
            }
        return selected;
    }

    public static List<Entry> search(Glossary g, String query, int maxTokens) {
        if (query == null || query.isBlank()) return List.of();
        var matches =
                g.entries().stream()
                        .filter(
                                e ->
                                        Json.write(e).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                        .toList();
        var out = new ArrayList<Entry>();
        int size = 0;
        for (var e : matches) {
            int n = Tokens.count(Json.write(e));
            if (size + n <= maxTokens) {
                out.add(e);
                size += n;
            }
        }
        return out;
    }

    public static boolean sameEntity(Entry a, Entry b) {
        return a.key().equals(b.key())
                || a.japanese().equals(b.japanese())
                || a.aliases().contains(b.key())
                || a.aliases().contains(b.japanese())
                || b.aliases().contains(a.key())
                || b.aliases().contains(a.japanese());
    }

    public static void validate(Entry e) {
        if (e.key() == null
                || e.key().isBlank()
                || e.japanese() == null
                || e.japanese().isBlank()
                || e.ukrainian() == null
                || e.aliases() == null
                || e.aliases().stream().anyMatch(Objects::isNull)
                || e.reading() == null
                || e.facts() == null
                || e.kind() == null
                || !List.of("confirmed", "assumed", "unknown").contains(e.certainty())
                || !List.of("male", "female", "other", "unknown").contains(e.gender()))
            throw new IllegalArgumentException("Invalid dictionary entry");
    }
}
