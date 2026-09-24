package space.panrid.novelka.account.internal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import space.panrid.novelka.platform.web.UserFacingException;

/** What counts as a valid nick, email and password. Messages are shown to people as is. */
final class AccountRules {

    static final int NICK_MIN = 3;
    static final int NICK_MAX = 30;
    static final int PASSWORD_MIN = 10;
    static final int PASSWORD_MAX_BYTES = 72; // BCrypt ignores anything longer
    static final int BIO_MAX = 500;

    // Latin or Ukrainian Cyrillic letters, digits, "_" and "-"; starts with a letter or digit.
    private static final Pattern NICK = Pattern.compile("^[\\p{IsLatin}\\p{IsCyrillic}\\d][\\p{IsLatin}\\p{IsCyrillic}\\d_-]*$");
    private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "moderator", "owner", "support", "system", "novelka", "новелка",
            "адмін", "модератор", "api", "me", "null", "undefined");

    private AccountRules() {
    }

    static String nick(String raw) {
        String nick = raw == null ? "" : raw.strip();
        if (nick.length() < NICK_MIN || nick.length() > NICK_MAX) {
            throw UserFacingException.badRequest("Нік має бути від %d до %d символів.".formatted(NICK_MIN, NICK_MAX));
        }
        if (!NICK.matcher(nick).matches()) {
            throw UserFacingException.badRequest(
                    "У ніку можуть бути лише літери, цифри, «_» і «-», і починатися він має з літери чи цифри.");
        }
        // «раnrid» with a Cyrillic «а» would pass for «panrid»: one alphabet per nick.
        if (LATIN.matcher(nick).find() && CYRILLIC.matcher(nick).find()) {
            throw UserFacingException.badRequest("Нік має бути або латиницею, або кирилицею, не впереміш.");
        }
        if (RESERVED.contains(key(nick))) {
            throw UserFacingException.badRequest("Цей нік зарезервовано. Оберіть інший.");
        }
        return nick;
    }

    static String email(String raw) {
        String email = raw == null ? "" : raw.strip();
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw UserFacingException.badRequest("Перевірте адресу пошти: схоже, в ній помилка.");
        }
        return email;
    }

    static String password(String raw) {
        String password = raw == null ? "" : raw;
        if (password.length() < PASSWORD_MIN) {
            throw UserFacingException.badRequest("Пароль має бути щонайменше з %d символів.".formatted(PASSWORD_MIN));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw UserFacingException.badRequest("Пароль задовгий. Скоротіть його до 72 латинських символів.");
        }
        return password;
    }

    static String bio(String raw) {
        String bio = raw == null ? "" : raw.strip();
        if (bio.length() > BIO_MAX) {
            throw UserFacingException.badRequest("«Про себе» — до %d символів.".formatted(BIO_MAX));
        }
        return bio;
    }

    /** Comparison key for nicks and emails: case does not make a different account. */
    static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
