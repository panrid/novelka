package space.panrid.novelka.platform.text;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Names people type after @ and $: user nicks and team handles. Latin or Ukrainian
 * Cyrillic letters, digits, «_» and «-»; one alphabet per name, so «раnrid» with a
 * Cyrillic «а» cannot pass for «panrid».
 */
public final class Handles {

    public static final int MIN = 3;
    public static final int MAX = 30;

    private static final Pattern SHAPE = Pattern.compile("^[\\p{IsLatin}\\p{IsCyrillic}\\d][\\p{IsLatin}\\p{IsCyrillic}\\d_-]*$");
    private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "moderator", "owner", "support", "system", "novelka", "новелка",
            "адмін", "модератор", "api", "me", "null", "undefined", "all", "everyone", "усі");

    private Handles() {
    }

    /** @param noun «Нік» or «Адреса команди», used in the messages */
    public static String check(String raw, String noun) {
        String value = raw == null ? "" : raw.strip();
        if (value.length() < MIN || value.length() > MAX) {
            throw UserFacingException.badRequest("%s має бути від %d до %d символів.".formatted(noun, MIN, MAX));
        }
        if (!SHAPE.matcher(value).matches()) {
            throw UserFacingException.badRequest(
                    "У полі «%s» можуть бути лише літери, цифри, «_» і «-», а починатися воно має з літери чи цифри."
                            .formatted(noun));
        }
        if (LATIN.matcher(value).find() && CYRILLIC.matcher(value).find()) {
            throw UserFacingException.badRequest("%s має бути або латиницею, або кирилицею, не впереміш.".formatted(noun));
        }
        if (RESERVED.contains(key(value))) {
            throw UserFacingException.badRequest("Це слово зарезервовано. Оберіть інше.");
        }
        return value;
    }

    public static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
