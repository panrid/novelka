package panrid.space.novelka.server.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.mail.Letter;
import panrid.space.novelka.server.mail.MailService;
import panrid.space.novelka.server.repository.AccountRepository;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.EmailTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Password reset and email confirmation by one-time links. A link carries a random token; only its SHA-256 is stored.
 * Reset links live 30 minutes, confirmation links a day, and each works once. A reset request answers the same way
 * whether or not the address exists, and a used reset link also confirms the address it was sent to.
 */
@Service
public final class AccountEmailService {
    static final int RESET_MINUTES = 30;
    static final int VERIFY_MINUTES = 24 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ReaderDatabase database;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final String publicUrl;

    public AccountEmailService(ReaderDatabase database, PasswordEncoder encoder, MailService mail,
            @Value("${novelka.public-url}") String publicUrl) {
        this.database = database; this.encoder = encoder; this.mail = mail;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    /** Sends a reset link if the address belongs to an account and the limits allow; never reveals which happened. */
    public void requestReset(String value) throws Exception {
        String email = AccountService.email(value);
        try (var jdbc = database.open()) {
            var account = new AccountRepository(jdbc).byEmail(email);
            if (account == null || !allowed(jdbc, (String) account.get("id"), "reset")) return;
            String token = issue(jdbc, (String) account.get("id"), "reset", email, RESET_MINUTES);
            String link = publicUrl + "/#/reset-password?token=" + token;
            mail.send(letter(email, "Відновлення пароля — Новелка", (String) account.get("username"),
                    "Хтось попросив змінити пароль до вашого акаунта на Новелці. Щоб задати новий пароль, відкрийте посилання (діє 30 хвилин):",
                    link, "Задати новий пароль", "Якщо це були не ви, просто проігноруйте лист: пароль не зміниться."));
        }
    }

    public void resetPassword(String token, String password) throws Exception {
        AccountService.validatePassword(password);
        try (var jdbc = database.open()) {
            jdbc.transaction(() -> {
                var tokens = new EmailTokenRepository(jdbc);
                var used = tokens.consume(hash(token), "reset");
                var accounts = new AccountRepository(jdbc);
                if (used == null || !used.get("email").equals(accounts.email((String) used.get("account_id"))))
                    throw new IllegalArgumentException("Посилання недійсне або застаріле. Попросіть новий лист.");
                String account = (String) used.get("account_id");
                accounts.updatePassword(account, encoder.encode(password));
                accounts.markVerified(account, (String) used.get("email"));
                tokens.revoke(account, "reset");
                new AuditRepository(jdbc).add(account, "account.password-reset", account, Map.of());
                return null;
            });
        }
    }

    /** Confirmation requested from the profile: refused while a previous letter is fresh or the daily limit is reached. */
    public void requestVerification(Account account) throws Exception {
        try (var jdbc = database.open()) {
            var accounts = new AccountRepository(jdbc);
            if (accounts.emailVerified(account.id())) throw new IllegalArgumentException("Email уже підтверджено.");
            String email = accounts.email(account.id());
            if (email == null) throw new IllegalArgumentException("Спочатку вкажіть email.");
            if (!allowed(jdbc, account.id(), "verify"))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Лист уже надіслано. Перевірте пошту або спробуйте за хвилину.");
            sendVerification(jdbc, account.id(), account.username(), email);
        }
    }

    /** After registration or an address change; silently skipped when the limits are reached. */
    public void verificationAfterChange(Account account) throws Exception {
        try (var jdbc = database.open()) {
            String email = new AccountRepository(jdbc).email(account.id());
            if (email == null || !allowed(jdbc, account.id(), "verify")) return;
            new EmailTokenRepository(jdbc).revoke(account.id(), "verify");
            sendVerification(jdbc, account.id(), account.username(), email);
        }
    }

    public void verify(String token) throws Exception {
        try (var jdbc = database.open()) {
            jdbc.transaction(() -> {
                var used = new EmailTokenRepository(jdbc).consume(hash(token), "verify");
                if (used == null || !new AccountRepository(jdbc).markVerified((String) used.get("account_id"), (String) used.get("email")))
                    throw new IllegalArgumentException("Посилання недійсне або застаріле. Надішліть новий лист із профілю.");
                new AuditRepository(jdbc).add((String) used.get("account_id"), "account.email-verified", (String) used.get("account_id"), Map.of());
                return null;
            });
        }
    }

    private void sendVerification(JdbcSession jdbc, String account, String username, String email) throws Exception {
        String token = issue(jdbc, account, "verify", email, VERIFY_MINUTES);
        mail.send(letter(email, "Підтвердіть email — Новелка", username,
                "Підтвердіть, що ця адреса належить вам. Так ви зможете відновити пароль, якщо забудете його. Посилання діє добу:",
                publicUrl + "/#/verify-email?token=" + token, "Підтвердити email",
                "Якщо ви не реєструвалися на Новелці, проігноруйте лист."));
    }

    /** At most one letter a minute and five a day per account and purpose, so the form cannot be used to flood a mailbox. */
    private static boolean allowed(JdbcSession jdbc, String account, String purpose) throws Exception {
        var recent = new EmailTokenRepository(jdbc).recent(account, purpose);
        return (recent.get("last") == null || ((Number) recent.get("last")).doubleValue() >= 60)
                && ((Number) recent.get("today")).intValue() < 5;
    }

    private static String issue(JdbcSession jdbc, String account, String purpose, String email, int minutes) throws Exception {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        new EmailTokenRepository(jdbc).create(account, purpose, hash(token), email, minutes);
        return token;
    }

    static String hash(String token) throws Exception {
        if (token == null || token.length() > 100) return "";
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private static Letter letter(String to, String subject, String username, String intro, String link, String action, String outro) {
        String text = "Вітаємо, " + username + "!\n\n" + intro + "\n\n" + link + "\n\n" + outro + "\n\n— Новелка";
        String html = "<p>Вітаємо, " + HtmlUtils.htmlEscape(username) + "!</p><p>" + HtmlUtils.htmlEscape(intro) + "</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;padding:10px 18px;background:#8a3b2e;"
                + "color:#fffaf0;border-radius:6px;text-decoration:none\">" + HtmlUtils.htmlEscape(action) + "</a></p>"
                + "<p style=\"color:#666\">Або скопіюйте посилання: " + HtmlUtils.htmlEscape(link) + "</p>"
                + "<p style=\"color:#666\">" + HtmlUtils.htmlEscape(outro) + "</p><p>— Новелка</p>";
        return new Letter(to, subject, text, html);
    }
}
