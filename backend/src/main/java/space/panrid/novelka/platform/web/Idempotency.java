package space.panrid.novelka.platform.web;

import static space.panrid.novelka.jooq.Tables.IDEMPOTENCY_KEY;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.function.Supplier;

import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@code Idempotency-Key} (етап 22.10): a request sent again with the same key gets the answer of
 * the first one, byte for byte, and changes nothing twice — a script that fell over halfway is
 * simply run again. The same key with a different request is refused (409): it is a mistake in
 * the script, not a retry.
 *
 * <p>Keys are a person's own, whatever token sent them, and kept {@link #KEPT 7 days}. Only answers
 * that succeeded are kept: the key is taken in the same transaction as the work, so a refused or
 * failed request leaves nothing behind and may be tried again with its key. Two requests with one
 * key at once are serialised by the key's row: the second waits and gets the first one's answer.
 */
@Component
public class Idempotency {

    public static final String HEADER = "Idempotency-Key";
    /** Says that the answer is the one kept from the first request. */
    public static final String REPLAYED = "Idempotent-Replayed";
    public static final Duration KEPT = Duration.ofDays(7);
    private static final int MAX_KEY = 255;

    private static final Logger log = LoggerFactory.getLogger(Idempotency.class);

    private final DSLContext db;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate transaction;

    Idempotency(DSLContext db, JsonMapper json, Clock clock, PlatformTransactionManager transactions) {
        this.db = db;
        this.json = json;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** What the work answers: its status and the body to send as JSON. */
    public record Answer(HttpStatus status, Object body) {
    }

    /**
     * Does the work once per key: the first time it runs and its answer is kept; again with the same
     * request, the kept answer is sent. Without a key the work simply runs.
     *
     * @param accountId whose key it is
     * @param key       the {@code Idempotency-Key} header, if sent
     * @param request   what makes the request this request — the operation, its target and its body;
     *                  the same key with another one is refused
     */
    public ResponseEntity<String> once(long accountId, @Nullable String key, String request, Supplier<Answer> work) {
        if (key == null) {
            Answer answer = work.get();
            return ResponseEntity.status(answer.status()).contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(answer.body()));
        }
        String checked = checked(key);
        String fingerprint = sha256(request);
        Kept kept = transaction.execute(status -> {
            Kept earlier = claim(accountId, checked, fingerprint);
            if (earlier != null) {
                return earlier;
            }
            Answer answer = work.get();
            String body = json.writeValueAsString(answer.body());
            db.update(IDEMPOTENCY_KEY).set(IDEMPOTENCY_KEY.STATUS, answer.status().value()).set(IDEMPOTENCY_KEY.BODY, body)
                    .where(IDEMPOTENCY_KEY.ACCOUNT_ID.eq(accountId), IDEMPOTENCY_KEY.KEY.eq(checked)).execute();
            return new Kept(answer.status().value(), body, false);
        });
        if (kept == null) {
            throw new IllegalStateException("The transaction gave no answer");
        }
        return ResponseEntity.status(kept.status()).contentType(MediaType.APPLICATION_JSON).header(REPLAYED, String.valueOf(kept.replayed()))
                .body(kept.body());
    }

    private record Kept(int status, String body, boolean replayed) {
    }

    /**
     * Takes the key for this request, or finds the answer kept for it. An insert that meets a key
     * another transaction has just taken waits for it: committed — its answer is here; rolled back —
     * the key is this request's.
     */
    private @Nullable Kept claim(long accountId, String key, String fingerprint) {
        OffsetDateTime now = now();
        int taken = db.insertInto(IDEMPOTENCY_KEY).set(IDEMPOTENCY_KEY.ACCOUNT_ID, accountId).set(IDEMPOTENCY_KEY.KEY, key)
                .set(IDEMPOTENCY_KEY.FINGERPRINT, fingerprint).set(IDEMPOTENCY_KEY.STATUS, 0).set(IDEMPOTENCY_KEY.BODY, "")
                .set(IDEMPOTENCY_KEY.CREATED_AT, now).onConflictDoNothing().execute();
        if (taken == 1) {
            return null;
        }
        // Older than a week and not yet swept away: forgotten, the key is free again.
        int renewed = db.update(IDEMPOTENCY_KEY).set(IDEMPOTENCY_KEY.FINGERPRINT, fingerprint).set(IDEMPOTENCY_KEY.STATUS, 0)
                .set(IDEMPOTENCY_KEY.BODY, "").set(IDEMPOTENCY_KEY.CREATED_AT, now)
                .where(IDEMPOTENCY_KEY.ACCOUNT_ID.eq(accountId), IDEMPOTENCY_KEY.KEY.eq(key),
                        IDEMPOTENCY_KEY.CREATED_AT.lt(now.minus(KEPT)))
                .execute();
        if (renewed == 1) {
            return null;
        }
        Record3<String, Integer, String> earlier = db.select(IDEMPOTENCY_KEY.FINGERPRINT, IDEMPOTENCY_KEY.STATUS, IDEMPOTENCY_KEY.BODY)
                .from(IDEMPOTENCY_KEY).where(IDEMPOTENCY_KEY.ACCOUNT_ID.eq(accountId), IDEMPOTENCY_KEY.KEY.eq(key))
                .forUpdate().fetchSingle();
        if (!earlier.value1().equals(fingerprint)) {
            throw new UserFacingException(HttpStatus.CONFLICT, "Ключ " + HEADER + " «" + key + "» уже використано для іншого запиту "
                    + "(інша адреса чи інше тіло). Повтор має бути точно таким самим; для нового запиту візьміть новий ключ.",
                    "idempotency-key-reused");
        }
        return new Kept(earlier.value2(), earlier.value3(), true);
    }

    private static String checked(String key) {
        if (key.isEmpty() || key.length() > MAX_KEY || !key.chars().allMatch(c -> c > 0x20 && c < 0x7f)) {
            throw UserFacingException.badRequest(HEADER + " — від 1 до 255 латинських літер, цифр і знаків без пробілів, "
                    + "наприклад UUID.");
        }
        return key;
    }

    /** Once an hour: keys older than a week are forgotten. */
    @Scheduled(initialDelay = 1_200_000, fixedDelay = 3_600_000)
    public void forgetOld() {
        int forgotten = db.deleteFrom(IDEMPOTENCY_KEY).where(IDEMPOTENCY_KEY.CREATED_AT.lt(now().minus(KEPT))).execute();
        if (forgotten > 0) {
            log.info("Idempotency keys forgotten: {}", forgotten);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
