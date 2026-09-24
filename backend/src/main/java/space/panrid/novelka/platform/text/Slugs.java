package space.panrid.novelka.platform.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Readable URL parts from Ukrainian titles: «Маг води» → {@code mah-vody}.
 * Transliteration follows the official Ukrainian table (КМУ №55, 2010), including the
 * word-initial forms of є, ї, й, ю, я and «зг» → «zgh».
 */
public final class Slugs {

    private static final Map<Character, String> LETTERS = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "h"),
            Map.entry('ґ', "g"), Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('є', "ie"),
            Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "y"), Map.entry('і', "i"),
            Map.entry('ї', "i"), Map.entry('й', "i"), Map.entry('к', "k"), Map.entry('л', "l"),
            Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"), Map.entry('п', "p"),
            Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"),
            Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "shch"), Map.entry('ь', ""), Map.entry('ю', "iu"),
            Map.entry('я', "ia"), Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('э', "e"), Map.entry('ё', "io"));
    private static final Map<Character, String> WORD_START = Map.of(
            'є', "ye", 'ї', "yi", 'й', "y", 'ю', "yu", 'я', "ya");

    private Slugs() {
    }

    public static String transliterate(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length() + 8);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            // After an apostrophe the word goes on: «м'ята» → «miata», not «myata».
            boolean wordStart = i == 0 || !Character.isLetter(lower.charAt(i - 1)) && !isApostrophe(lower.charAt(i - 1));
            if (c == 'г' && i > 0 && lower.charAt(i - 1) == 'з') {
                out.append("gh");
            } else if (wordStart && WORD_START.containsKey(c)) {
                out.append(WORD_START.get(c));
            } else if (LETTERS.containsKey(c)) {
                out.append(LETTERS.get(c));
            } else if (isApostrophe(c)) {
                // apostrophe is dropped: «м'ята» → «miata»
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isApostrophe(char c) {
        return c == '\'' || c == 'ʼ' || c == '’';
    }

    /** Lowercase Latin letters, digits and single hyphens, at most {@code max} characters. */
    public static String slug(String text, int max) {
        String ascii = Normalizer.normalize(transliterate(text), Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.length() > max) {
            slug = slug.substring(0, max).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "n" : slug;
    }
}
