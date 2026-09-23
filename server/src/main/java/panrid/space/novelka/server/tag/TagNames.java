package panrid.space.novelka.server.tag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Tag display names keep their casing; the slug ignores case, Unicode forms and extra spaces. */
public final class TagNames {
    public static final String MACHINE_TRANSLATION = "машинний переклад";
    private static final Locale UKRAINIAN = Locale.forLanguageTag("uk");

    private TagNames() {}

    public static String name(String value) {
        if (value == null) throw new IllegalArgumentException("Вкажіть назву тегу.");
        String name = Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 40 || name.chars().anyMatch(Character::isISOControl) || name.contains("|"))
            throw new IllegalArgumentException("Тег: від 1 до 40 символів, без «|».");
        return name;
    }

    public static String slug(String value) { return name(value).toLowerCase(UKRAINIAN); }

    /** Unique tags in the given order; the first spelling of a duplicate wins. */
    public static List<String> names(List<String> values) {
        if (values == null) return List.of();
        var unique = new LinkedHashMap<String, String>();
        for (var value : values) unique.putIfAbsent(slug(value), name(value));
        if (unique.size() > 12) throw new IllegalArgumentException("Новела може мати до 12 тегів.");
        return new ArrayList<>(unique.values());
    }
}
