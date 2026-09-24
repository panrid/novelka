package space.panrid.novelka.account.internal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import space.panrid.novelka.platform.text.Handles;
import space.panrid.novelka.platform.web.UserFacingException;

/** What counts as a valid nick, email and password. Messages are shown to people as is. */
final class AccountRules {

    static final int PASSWORD_MIN = 10;
    static final int PASSWORD_MAX_BYTES = 72; // BCrypt ignores anything longer
    static final int BIO_MAX = 500;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private AccountRules() {
    }

    static String nick(String raw) {
        return Handles.check(raw, "Нік");
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
