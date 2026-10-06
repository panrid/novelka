package space.panrid.novelka.autotranslate.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A glossary word in the Ukrainian text in any of its forms (етап 17): «Орест» finds «Оресте»,
 * «Ореста», «Орестові». A word is matched by its stem (the form without a final vowel) and up
 * to three more letters; a changed word keeps the ending it had, so «Ореста» → «Остапа».
 * Case agreement past that is for the reviewer, or for the model when asked.
 */
final class WordForms {

    private static final String LETTER = "[\\p{L}ʼ'’]";
    private static final String VOWELS = "аеєиіїоуюяйь";

    private WordForms() {
    }

    static String stem(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        // An adjective drops its whole ending: «Крижаний» → «Крижан» to find «Крижаного».
        if (word.length() >= 5 && (lower.endsWith("ий") || lower.endsWith("ій") || lower.endsWith("ей"))) {
            return word.substring(0, word.length() - 2);
        }
        return word.length() >= 4 && VOWELS.indexOf(lower.charAt(lower.length() - 1)) >= 0
                ? word.substring(0, word.length() - 1) : word;
    }

    static Pattern pattern(String form) {
        String[] words = form.strip().split("\\s+");
        StringBuilder regex = new StringBuilder("(?<!").append(LETTER).append(")");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                regex.append("\\s+");
            }
            regex.append("(").append(Pattern.quote(stem(words[i]))).append(LETTER).append("{0,3})");
        }
        regex.append("(?!").append(LETTER).append(")");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Where the form occurs in a text: [start, end) of each match. */
    static List<int[]> find(String text, String form) {
        List<int[]> out = new ArrayList<>();
        if (form == null || form.isBlank()) {
            return out;
        }
        Matcher matcher = pattern(form).matcher(text);
        while (matcher.find()) {
            out.add(new int[] {matcher.start(), matcher.end()});
        }
        return out;
    }

    /** The text with every form of {@code from} turned into the same form of {@code to}. */
    static String replace(String text, String from, String to) {
        String[] oldWords = from.strip().split("\\s+");
        String[] newWords = to.strip().split("\\s+");
        Matcher matcher = pattern(from).matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replaced;
            if (oldWords.length == newWords.length) {
                StringBuilder words = new StringBuilder();
                for (int i = 0; i < oldWords.length; i++) {
                    if (i > 0) {
                        words.append(' ');
                    }
                    words.append(word(matcher.group(i + 1), oldWords[i], newWords[i]));
                }
                replaced = words.toString();
            } else {
                replaced = cased(to.strip(), matcher.group());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replaced));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String word(String found, String oldWord, String newWord) {
        String ending = found.substring(Math.min(found.length(), stem(oldWord).length()));
        String dropped = oldWord.substring(stem(oldWord).length());
        // The form as the glossary wrote it gives the new word as written; another ending goes onto its stem.
        String result = ending.equalsIgnoreCase(dropped) ? newWord : joined(stem(newWord), ending);
        return cased(result, found);
    }

    /** ї goes after a vowel or an apostrophe, і after a consonant: «Марії» → «Мірелі». */
    private static String joined(String stem, String ending) {
        if (stem.isEmpty() || ending.isEmpty()) {
            return stem + ending;
        }
        char last = Character.toLowerCase(stem.charAt(stem.length() - 1));
        boolean soft = "аеєиіїоуюяʼ'’".indexOf(last) >= 0;
        char first = ending.charAt(0);
        if (first == 'ї' && !soft) {
            return stem + 'і' + ending.substring(1);
        }
        if (first == 'і' && soft) {
            return stem + 'ї' + ending.substring(1);
        }
        return stem + ending;
    }

    /** The first letter capitalised as it was in the text. */
    private static String cased(String word, String like) {
        if (word.isEmpty() || like.isEmpty()) {
            return word;
        }
        boolean upper = Character.isUpperCase(like.codePointAt(0));
        String first = word.substring(0, 1);
        return (upper ? first.toUpperCase(Locale.ROOT) : first.toLowerCase(Locale.ROOT)) + word.substring(1);
    }
}
