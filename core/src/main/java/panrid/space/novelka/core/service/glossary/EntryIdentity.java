package panrid.space.novelka.core.service.glossary;

import panrid.space.novelka.core.model.Entry;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Deterministic identity hints; matching translated names alone never authorizes an automatic merge. */
public final class EntryIdentity {
    private EntryIdentity() { }

    public static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean equal(String a, String b) {
        return !normalize(a).isEmpty() && normalize(a).equals(normalize(b));
    }

    public static boolean same(Entry a, Entry b) {
        return equal(a.key(), b.key()) || equal(a.japanese(), b.japanese())
                || a.aliases().stream().anyMatch(alias -> equal(alias, b.key()) || equal(alias, b.japanese()))
                || b.aliases().stream().anyMatch(alias -> equal(alias, a.key()) || equal(alias, a.japanese()));
    }

    public static boolean possible(Entry a, Entry b) {
        return same(a, b) || equal(a.kind(), b.kind())
                && (equal(a.ukrainian(), b.ukrainian()) || equal(a.reading(), b.reading()));
    }

    public static List<String> differences(Entry existing, Entry proposal) {
        var result = new ArrayList<String>();
        add(result, "Тип", existing.kind(), proposal.kind());
        add(result, "Японське написання", existing.japanese(), proposal.japanese());
        add(result, "Український відповідник", existing.ukrainian(), proposal.ukrainian());
        add(result, "Читання", existing.reading(), proposal.reading());
        add(result, "Стать", existing.gender(), proposal.gender());
        if (!normalize(proposal.facts()).isEmpty() && !normalize(existing.facts()).contains(normalize(proposal.facts()))) result.add("Факти");
        var known = Stream.concat(Stream.of(existing.key(), existing.japanese(), existing.ukrainian(), existing.reading()), existing.aliases().stream())
                .map(EntryIdentity::normalize).toList();
        if (proposal.aliases().stream().map(EntryIdentity::normalize).anyMatch(alias -> !alias.isEmpty() && !known.contains(alias))) result.add("Інші написання");
        if (!normalize(existing.certainty()).equals("confirmed")) add(result, "Впевненість", existing.certainty(), proposal.certainty());
        return result;
    }

    private static void add(List<String> changes, String label, String existing, String proposed) {
        String value = normalize(proposed);
        if (!value.isEmpty() && !value.equals("unknown") && !value.equals(normalize(existing))) changes.add(label);
    }
}
