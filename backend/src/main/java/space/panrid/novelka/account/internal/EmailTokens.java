package space.panrid.novelka.account.internal;

import static space.panrid.novelka.jooq.Tables.EMAIL_TOKEN;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * One-time links from emails. The raw token lives only in the letter; the database keeps
 * its SHA-256, so a leaked backup cannot be used to confirm or reset anything.
 */
@Component
class EmailTokens {

    enum Purpose {
        VERIFY("verify", Duration.ofHours(24)),
        RESET("reset", Duration.ofMinutes(30)),
        CHANGE_EMAIL("change_email", Duration.ofHours(24));

        final String code;
        final Duration lifetime;

        Purpose(String code, Duration lifetime) {
            this.code = code;
            this.lifetime = lifetime;
        }
    }

    record Token(long accountId, String email) {
    }

    private final DSLContext db;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    EmailTokens(DSLContext db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    String issue(long accountId, Purpose purpose, String email) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime now = now();
        db.insertInto(EMAIL_TOKEN)
                .set(EMAIL_TOKEN.TOKEN_HASH, hash(raw))
                .set(EMAIL_TOKEN.ACCOUNT_ID, accountId)
                .set(EMAIL_TOKEN.PURPOSE, purpose.code)
                .set(EMAIL_TOKEN.EMAIL, email)
                .set(EMAIL_TOKEN.CREATED_AT, now)
                .set(EMAIL_TOKEN.EXPIRES_AT, now.plus(purpose.lifetime))
                .execute();
        return raw;
    }

    /** Uses the link once. Expired, used or foreign tokens are simply not found. */
    Optional<Token> consume(String raw, Purpose purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 100) {
            return Optional.empty();
        }
        OffsetDateTime now = now();
        return db.update(EMAIL_TOKEN)
                .set(EMAIL_TOKEN.USED_AT, now)
                .where(EMAIL_TOKEN.TOKEN_HASH.eq(hash(raw))
                        .and(EMAIL_TOKEN.PURPOSE.eq(purpose.code))
                        .and(EMAIL_TOKEN.USED_AT.isNull())
                        .and(EMAIL_TOKEN.EXPIRES_AT.gt(now)))
                .returning(EMAIL_TOKEN.ACCOUNT_ID, EMAIL_TOKEN.EMAIL)
                .fetchOptional(r -> new Token(r.getAccountId(), r.getEmail()));
    }

    /** After a password reset, older reset links in other letters stop working. */
    void revokeAll(long accountId, Purpose purpose) {
        db.update(EMAIL_TOKEN)
                .set(EMAIL_TOKEN.USED_AT, now())
                .where(EMAIL_TOKEN.ACCOUNT_ID.eq(accountId)
                        .and(EMAIL_TOKEN.PURPOSE.eq(purpose.code))
                        .and(EMAIL_TOKEN.USED_AT.isNull()))
                .execute();
    }

    /** Letters sent recently: at most one a minute and five a day per account and purpose. */
    boolean mayIssue(long accountId, Purpose purpose) {
        OffsetDateTime now = now();
        var recent = EMAIL_TOKEN.ACCOUNT_ID.eq(accountId).and(EMAIL_TOKEN.PURPOSE.eq(purpose.code));
        boolean lastMinute = db.fetchExists(EMAIL_TOKEN, recent.and(EMAIL_TOKEN.CREATED_AT.gt(now.minusMinutes(1))));
        int today = db.fetchCount(EMAIL_TOKEN, recent.and(EMAIL_TOKEN.CREATED_AT.gt(now.minusDays(1))));
        return !lastMinute && today < 5;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
