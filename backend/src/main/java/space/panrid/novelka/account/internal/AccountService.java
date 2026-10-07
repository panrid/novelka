package space.panrid.novelka.account.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.AccountMessenger;
import space.panrid.novelka.account.GoogleSignIn;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.internal.EmailTokens.Purpose;
import space.panrid.novelka.platform.mail.Mailer;
import space.panrid.novelka.platform.tx.AfterCommit;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.web.UserFacingException;

@Service
class AccountService {

    static final String EMAIL_NOT_VERIFIED = "email-not-verified";
    static final Duration NICK_CHANGE_INTERVAL = Duration.ofDays(30);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BAD_CREDENTIALS = "Неправильний нік, пошта або пароль.";

    private final AccountRepository accounts;
    private final EmailTokens tokens;
    private final AccountMails mails;
    private final Mailer mailer;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final RateLimiter signInsByAddress;
    private final RateLimiter signInsByLogin;
    private final RateLimiter registrationsByAddress;
    private final SiteSettings settings;
    private final ObjectProvider<AccountMessenger> messengers;
    // Spent on unknown logins, so a wrong nick takes as long as a wrong password.
    private final String dummyHash;

    AccountService(AccountRepository accounts, EmailTokens tokens, AccountMails mails, Mailer mailer,
            PasswordEncoder passwords, Clock clock, AccountLimits limits, SiteSettings settings,
            ObjectProvider<AccountMessenger> messengers) {
        this.settings = settings;
        this.messengers = messengers;
        this.accounts = accounts;
        this.tokens = tokens;
        this.mails = mails;
        this.mailer = mailer;
        this.passwords = passwords;
        this.clock = clock;
        this.signInsByAddress = new RateLimiter(limits.signInsPerAddress(), Duration.ofMinutes(10), clock);
        this.signInsByLogin = new RateLimiter(limits.signInsPerLogin(), Duration.ofMinutes(10), clock);
        this.registrationsByAddress = new RateLimiter(limits.registrationsPerAddress(), Duration.ofHours(1), clock);
        this.dummyHash = passwords.encode("not a real password");
    }

    /** Creates an unconfirmed account and sends the confirmation letter. */
    @Transactional
    void register(String rawNick, String rawEmail, String rawPassword, String clientAddress) {
        requireRegistrationOpen(clientAddress);
        String nick = AccountRules.nick(rawNick);
        String email = AccountRules.email(rawEmail);
        String password = AccountRules.password(rawPassword);
        if (accounts.nickTaken(nick)) {
            throw UserFacingException.conflict("Цей нік уже зайнятий. Оберіть інший.");
        }
        if (accounts.emailTaken(email)) {
            throw UserFacingException.conflict("Акаунт із цією поштою вже є. Увійдіть або відновіть пароль.");
        }
        long id = accounts.insert(nick, email, passwords.encode(password), SiteRole.READER, null);
        String token = tokens.issue(id, Purpose.VERIFY, email);
        AfterCommit.run(() -> mailer.send(mails.verify(email, nick, token)));
    }

    /** Confirms the email from the letter; the caller signs the person in. */
    @Transactional
    long verify(String token) {
        EmailTokens.Token used = tokens.consume(token, Purpose.VERIFY).orElseThrow(AccountService::staleLink);
        AccountRow account = accounts.byId(used.accountId()).orElseThrow(AccountService::staleLink);
        // A letter sent to an address the account no longer uses confirms nothing.
        if (!AccountRules.key(account.email()).equals(AccountRules.key(used.email()))) {
            throw staleLink();
        }
        accounts.markEmailVerified(account.id(), now());
        return account.id();
    }

    /** Sends the confirmation letter again. Silent about unknown addresses. */
    @Transactional
    void resendVerification(String email) {
        accounts.byEmail(email)
                .filter(account -> !account.emailVerified())
                .filter(account -> tokens.mayIssue(account.id(), Purpose.VERIFY))
                .ifPresent(account -> {
                    String token = tokens.issue(account.id(), Purpose.VERIFY, account.email());
                    AfterCommit.run(() -> mailer.send(mails.verify(account.email(), account.nick(), token)));
                });
    }

    long authenticate(String login, String password, String clientAddress) {
        String loginKey = login == null ? "" : AccountRules.key(login);
        if (!signInsByAddress.tryAcquire(clientAddress) || !signInsByLogin.tryAcquire(loginKey)) {
            throw UserFacingException.tooManyRequests();
        }
        Optional<AccountRow> found = loginKey.isEmpty() ? Optional.empty() : accounts.byLogin(loginKey);
        String hash = found.map(AccountRow::passwordHash).orElse(dummyHash);
        boolean matches = password != null && passwords.matches(password, hash);
        if (found.isEmpty() || !matches) {
            throw new UserFacingException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        AccountRow account = found.get();
        if (!account.emailVerified()) {
            throw new UserFacingException(HttpStatus.FORBIDDEN,
                    "Спершу підтвердьте пошту: ми надіслали лист на %s.".formatted(account.email()),
                    EMAIL_NOT_VERIFIED);
        }
        return account.id();
    }

    /** Sends a reset letter. Always looks the same from outside, known address or not. */
    @Transactional
    void requestPasswordReset(String email) {
        accounts.byEmail(email == null ? "" : email)
                .filter(account -> tokens.mayIssue(account.id(), Purpose.RESET))
                .ifPresent(account -> {
                    String token = tokens.issue(account.id(), Purpose.RESET, account.email());
                    AfterCommit.run(() -> mailer.send(mails.reset(account.email(), account.nick(), token)));
                    tell(account.id(), "Посилання, щоб задати новий пароль на Новелці. Діє 30 хвилин. "
                            + "Якщо ви не просили — нічого не робіть.", mails.resetLink(token));
                });
    }

    /** Sets the new password from the letter. The letter also proves the address works. */
    @Transactional
    long resetPassword(String token, String rawPassword) {
        String password = AccountRules.password(rawPassword);
        EmailTokens.Token used = tokens.consume(token, Purpose.RESET).orElseThrow(AccountService::staleLink);
        AccountRow account = accounts.byId(used.accountId()).orElseThrow(AccountService::staleLink);
        if (!AccountRules.key(account.email()).equals(AccountRules.key(used.email()))) {
            throw staleLink();
        }
        accounts.updatePassword(account.id(), passwords.encode(password));
        accounts.markEmailVerified(account.id(), now());
        tokens.revokeAll(account.id(), Purpose.RESET);
        return account.id();
    }

    @Transactional
    void changeNick(long id, String rawNick) {
        String nick = AccountRules.nick(rawNick);
        AccountRow account = accounts.byId(id).orElseThrow();
        if (account.nick().equals(nick)) {
            return;
        }
        OffsetDateTime now = now();
        if (account.nickChangedAt() != null && account.nickChangedAt().plus(NICK_CHANGE_INTERVAL).isAfter(now)) {
            String next = account.nickChangedAt().plus(NICK_CHANGE_INTERVAL)
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.forLanguageTag("uk")));
            throw new UserFacingException(HttpStatus.TOO_MANY_REQUESTS,
                    "Нік можна змінювати раз на 30 днів. Наступного разу — з %s.".formatted(next));
        }
        // Changing only the letter case keeps the same nick key, so it is not "taken".
        if (!AccountRules.key(account.nick()).equals(AccountRules.key(nick)) && accounts.nickTaken(nick)) {
            throw UserFacingException.conflict("Цей нік уже зайнятий. Оберіть інший.");
        }
        accounts.changeNick(id, account.nick(), nick, now);
        tell(id, "Ваш нік на Новелці тепер «%s».".formatted(nick), null);
    }

    /** The new address gets a link; the old one stays in use until it is opened. */
    @Transactional
    void requestEmailChange(long id, String rawEmail, String password) {
        String email = AccountRules.email(rawEmail);
        AccountRow account = accounts.byId(id).orElseThrow();
        requirePassword(account, password);
        if (AccountRules.key(account.email()).equals(AccountRules.key(email))) {
            throw UserFacingException.badRequest("Це й так ваша пошта.");
        }
        if (accounts.emailTaken(email)) {
            throw UserFacingException.conflict("Ця пошта вже належить іншому акаунту.");
        }
        if (!tokens.mayIssue(id, Purpose.CHANGE_EMAIL)) {
            throw UserFacingException.tooManyRequests();
        }
        String token = tokens.issue(id, Purpose.CHANGE_EMAIL, email);
        AfterCommit.run(() -> mailer.send(mails.confirmNewEmail(email, account.nick(), token)));
    }

    @Transactional
    long confirmEmailChange(String token) {
        EmailTokens.Token used = tokens.consume(token, Purpose.CHANGE_EMAIL).orElseThrow(AccountService::staleLink);
        AccountRow account = accounts.byId(used.accountId()).orElseThrow(AccountService::staleLink);
        if (accounts.emailTaken(used.email())) {
            throw UserFacingException.conflict("Ця пошта вже належить іншому акаунту.");
        }
        accounts.updateEmail(account.id(), used.email());
        accounts.markEmailVerified(account.id(), now());
        AfterCommit.run(() -> mailer.send(mails.emailChanged(account.email(), account.nick(), used.email())));
        tell(account.id(), "Пошту акаунта змінено на %s. Якщо це були не ви — відновіть пароль.".formatted(used.email()),
                mails.resetPage());
        return account.id();
    }

    @Transactional
    void changePassword(long id, String currentPassword, String rawNewPassword) {
        AccountRow account = accounts.byId(id).orElseThrow();
        requirePassword(account, currentPassword);
        String password = AccountRules.password(rawNewPassword);
        accounts.updatePassword(id, passwords.encode(password));
        tokens.revokeAll(id, Purpose.RESET);
        AfterCommit.run(() -> mailer.send(mails.passwordChanged(account.email(), account.nick())));
        tell(id, "Пароль акаунта змінено. Якщо це були не ви — відновіть пароль.", mails.resetPage());
    }

    /** After the commit, to the person's messengers (Telegram) as well as the letters. */
    private void tell(long accountId, String text, String link) {
        AfterCommit.run(() -> messengers.orderedStream().forEach(messenger -> messenger.tell(accountId, text, link)));
    }

    /** The account a Google sign-in opens, if there is one already. */
    record GoogleMatch(long accountId, boolean tookOver) {
    }

    /**
     * Finds the account of whoever signed in with Google: the one tied to that Google account,
     * or the one with the same address, which gets tied now. Empty when the person is new.
     */
    @Transactional
    Optional<GoogleMatch> matchGoogle(GoogleSignIn.Identity identity) {
        Optional<AccountRow> tied = accounts.byGoogle(identity.subject());
        if (tied.isPresent()) {
            return Optional.of(new GoogleMatch(tied.get().id(), false));
        }
        if (!identity.emailVerified() || identity.email().isBlank()) {
            throw UserFacingException.badRequest("Google не підтвердив цю пошту, тож увійти через нього не вийде.");
        }
        Optional<AccountRow> same = accounts.byEmail(identity.email());
        if (same.isEmpty()) {
            return Optional.empty();
        }
        AccountRow account = same.get();
        if (account.googleSub() != null) {
            throw UserFacingException.conflict("Акаунт із цією поштою вже прив’язаний до іншого облікового запису Google.");
        }
        accounts.setGoogle(account.id(), identity.subject());
        if (account.emailVerified()) {
            return Optional.of(new GoogleMatch(account.id(), false));
        }
        // Somebody registered the address and never confirmed it. Google proves whose it is,
        // and the password chosen back then must not open the account any more.
        accounts.markEmailVerified(account.id(), now());
        accounts.unknownPassword(account.id(), passwords.encode(randomSecret()));
        return Optional.of(new GoogleMatch(account.id(), true));
    }

    /** A new account for a person Google vouched for: the address is confirmed, the password unknown. */
    @Transactional
    long registerWithGoogle(GoogleSignIn.Identity identity, String rawNick, String clientAddress) {
        requireRegistrationOpen(clientAddress);
        String nick = AccountRules.nick(rawNick);
        String email = AccountRules.email(identity.email());
        if (accounts.nickTaken(nick)) {
            throw UserFacingException.conflict("Цей нік уже зайнятий. Оберіть інший.");
        }
        if (accounts.byGoogle(identity.subject()).isPresent() || accounts.emailTaken(email)) {
            throw UserFacingException.conflict("Акаунт із цією поштою вже є. Увійдіть через Google ще раз.");
        }
        String hash = passwords.encode(randomSecret());
        long id = accounts.insert(nick, email, hash, SiteRole.READER, now());
        accounts.unknownPassword(id, hash);
        accounts.setGoogle(id, identity.subject());
        return id;
    }

    @Transactional
    void linkGoogle(long id, GoogleSignIn.Identity identity) {
        accounts.byGoogle(identity.subject()).filter(other -> other.id() != id).ifPresent(other -> {
            throw UserFacingException.conflict("Цей обліковий запис Google уже прив’язаний до іншого акаунта на Новелці.");
        });
        accounts.setGoogle(id, identity.subject());
    }

    @Transactional
    void unlinkGoogle(long id) {
        if (!accounts.byId(id).orElseThrow().passwordSet()) {
            throw UserFacingException.badRequest("Спершу задайте пароль, інакше в акаунт не буде як увійти.");
        }
        accounts.setGoogle(id, null);
    }

    private void requireRegistrationOpen(String clientAddress) {
        if (!settings.flag(SiteSettings.REGISTRATION_OPEN, true)) {
            throw new UserFacingException(HttpStatus.FORBIDDEN,
                    "Реєстрацію тимчасово закрито. Спробуйте пізніше.", "registration-closed");
        }
        if (!registrationsByAddress.tryAcquire(clientAddress)) {
            throw UserFacingException.tooManyRequests();
        }
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requirePassword(AccountRow account, String password) {
        if (!account.passwordSet()) {
            throw UserFacingException.badRequest("Пароля ще немає: задайте його листом у розділі «Пароль».");
        }
        if (password == null || !passwords.matches(password, account.passwordHash())) {
            throw UserFacingException.badRequest("Поточний пароль неправильний.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static UserFacingException staleLink() {
        return UserFacingException.badRequest(
                "Посилання вже не діє: його використали або минув строк. Попросіть новий лист.");
    }
}
