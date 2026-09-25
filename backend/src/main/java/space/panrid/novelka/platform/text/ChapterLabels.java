package space.panrid.novelka.platform.text;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The number a chapter shows, taken from its title: «第0話 プロローグ» → 0, «31.1話» → 31.1,
 * «第十二話» → 12, «Глава 7. Ніч» → 7. The rest of the title is what readers see after it.
 */
public final class ChapterLabels {

    /** A number, optionally with a sub-number: 31.1, 31-1, 31.5. */
    private static final String NUMBER = "([0-9]{1,5})(?:[.\\-・ー―][0-9]{1,3})?";
    private static final String AFTER = "(?=$|[\\s　:：.．、,，\\-－―—「『【(（〈《]|[話章部幕節回羽])";
    private static final Pattern JAPANESE_DIGITS = Pattern.compile(
            "^(?:第\\s*)?(" + NUMBER + ")\\s*(?:話|章|部|幕|節|回|羽)?" + AFTER);
    private static final Pattern JAPANESE_KANJI = Pattern.compile("^(?:第|其の)([〇零一二三四五六七八九十百千]+)(?:話|章|部|幕|節|回|羽)?");
    private static final Pattern UKRAINIAN = Pattern.compile(
            "^(?:(?:глава|розділ|епізод|серія|частина|chapter|episode)\\s*№?\\s*)?(" + NUMBER + ")(?:\\s*[.:)\\-–—]\\s*|\\s+|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern UKRAINIAN_WORD = Pattern.compile(
            "^(?:глава|розділ|епізод|серія|chapter|episode)\\s*№?\\s*(" + NUMBER + ")(?:\\s*[.:)\\-–—]\\s*|\\s+|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Chapters that are not numbered on purpose. */
    private static final Pattern SPECIAL = Pattern.compile(
            "プロローグ|エピローグ|序章|終章|幕間|閑話|番外|間章|断章|外伝|おまけ|あとがき|登場人物|人物紹介|設定|特別編|ＳＳ|SS");

    private ChapterLabels() {
    }

    /** The number in an original (Japanese) title, if it starts with one. */
    public static Optional<String> fromJapanese(String title) {
        String text = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKC).strip();
        Matcher digits = JAPANESE_DIGITS.matcher(text);
        if (digits.find()) {
            return Optional.of(clean(digits.group(1)));
        }
        Matcher kanji = JAPANESE_KANJI.matcher(text);
        if (kanji.find()) {
            int value = kanjiNumber(kanji.group(1));
            if (value >= 0 && value < 100_000) {
                return Optional.of(String.valueOf(value));
            }
        }
        return Optional.empty();
    }

    public static boolean isSpecial(String japaneseTitle) {
        return japaneseTitle != null && SPECIAL.matcher(Normalizer.normalize(japaneseTitle, Normalizer.Form.NFKC)).find();
    }

    /** A translated title without a leading number («Глава 3. Світло» → «Світло»). */
    public static String withoutNumber(String title) {
        String text = title == null ? "" : title.strip();
        Matcher matcher = UKRAINIAN.matcher(text);
        return matcher.find() ? text.substring(matcher.end()).strip() : text;
    }

    /** «Глава 31.1. Ніч» → label 31.1 and title «Ніч»; only with an explicit word, so «3 бажання» stays a title. */
    public static Optional<String[]> splitUkrainian(String title) {
        String text = title == null ? "" : title.strip();
        Matcher matcher = UKRAINIAN_WORD.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new String[] {clean(matcher.group(1)), text.substring(matcher.end()).strip()});
    }

    public static boolean valid(String label) {
        return label == null || label.isEmpty() || label.matches("[0-9]{1,5}(\\.[0-9]{1,3})?");
    }

    /** 031 → 31, 0000 → 0, 31-1 → 31.1. */
    private static String clean(String raw) {
        String[] parts = raw.split("[.\\-・ー―]");
        String main = parts[0].replaceFirst("^0+(?=\\d)", "");
        return parts.length > 1 ? main + "." + parts[1] : main;
    }

    private static int kanjiNumber(String kanji) {
        int total = 0;
        int current = 0;
        for (char c : kanji.toCharArray()) {
            int digit = "〇一二三四五六七八九".indexOf(c);
            if (c == '零') {
                digit = 0;
            }
            if (digit >= 0) {
                current = current * 10 + digit;
                continue;
            }
            int unit = switch (c) {
                case '十' -> 10;
                case '百' -> 100;
                case '千' -> 1000;
                default -> -1;
            };
            if (unit < 0) {
                return -1;
            }
            total += (current == 0 ? 1 : current) * unit;
            current = 0;
        }
        return total + current;
    }
}
