package space.panrid.novelka.apitoken.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.API_TOKEN;
import static space.panrid.novelka.jooq.Tables.API_TOKEN_WRITE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.AccountPrincipal;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.audit.AuditLog;
import space.panrid.novelka.platform.security.BearerTokens;
import space.panrid.novelka.platform.security.TokenGrant;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Access tokens (етап 22.1). A token is {@code nvk_} and 32 random bytes; it is shown once, when
 * made, and the database keeps only its SHA-256 — enough to recognise it, useless to sign in. A
 * token signs in as its owner, read afresh on every request, so it never gives more than the
 * owner has now; its scopes ({@link TokenScopes}) narrow that further.
 */
@Component
class ApiTokenService implements BearerTokens {

    static final String PREFIX = "nvk_";
    /** Live tokens one person may hold. */
    static final int MAX_LIVE = 20;
    static final List<Integer> DAYS = List.of(7, 30, 90, 365);
    /** «Коли й звідки» is written at most this often per token, unless the address changes. */
    private static final Duration TOUCH = Duration.ofMinutes(1);
    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * A token as its owner (or the site owner) sees it; never the token itself.
     *
     * @param hint  its first characters, «nvk_Ab3dE…»
     * @param state active, expired or revoked
     * @param owner whose it is (the site owner's list of all tokens)
     */
    record TokenView(long id, String name, String hint, List<String> scopes, OffsetDateTime createdAt, OffsetDateTime expiresAt,
            OffsetDateTime lastUsedAt, String lastUsedIp, OffsetDateTime revokedAt, String state, String owner) {
    }

    /** A token just made: the only time {@code secret} is told. */
    record Created(TokenView token, String secret) {
    }

    private final DSLContext db;
    private final AuditLog audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    ApiTokenService(DSLContext db, AuditLog audit, Clock clock) {
        this.db = db;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- managing tokens (session only) -------------------------------------------------------

    @Transactional
    Created create(Viewer viewer, String name, List<String> scopeCodes, Integer days) {
        String title = name == null ? "" : name.strip();
        if (title.isEmpty() || title.length() > 60) {
            throw UserFacingException.badRequest("Назвіть токен: до 60 символів, щоб потім знати, де він.");
        }
        if (days == null || !DAYS.contains(days)) {
            throw UserFacingException.badRequest("Строк дії — 7, 30, 90 чи 365 днів.");
        }
        Set<Scope> scopes = new LinkedHashSet<>();
        for (String code : scopeCodes == null ? List.<String>of() : scopeCodes) {
            scopes.add(Scope.of(code).orElseThrow(() -> UserFacingException.badRequest("Такого права немає: " + code + ".")));
        }
        if (scopes.isEmpty()) {
            throw UserFacingException.badRequest("Позначте хоча б одне право.");
        }
        if (!Scope.offeredTo(viewer.role()).containsAll(scopes)) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Право «admin» — лише для ролей сайту.");
        }
        // One at a time per person, so two quick clicks do not slip past the limit together.
        db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).forUpdate().execute();
        if (db.fetchCount(API_TOKEN, API_TOKEN.ACCOUNT_ID.eq(viewer.accountId()).and(live())) >= MAX_LIVE) {
            throw UserFacingException.conflict("Живих токенів може бути щонайбільше " + MAX_LIVE + ". Відкличте непотрібні.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long id = db.insertInto(API_TOKEN)
                .set(API_TOKEN.ACCOUNT_ID, viewer.accountId())
                .set(API_TOKEN.NAME, title)
                .set(API_TOKEN.HINT, secret.substring(0, PREFIX.length() + 6) + "…")
                .set(API_TOKEN.SECRET_HASH, hash(secret))
                .set(API_TOKEN.SCOPES, scopes.stream().map(Scope::code).toArray(String[]::new))
                .set(API_TOKEN.EXPIRES_AT, now().plusDays(days))
                .set(API_TOKEN.CREATED_AT, now())
                .returning(API_TOKEN.ID).fetchOne(API_TOKEN.ID);
        return new Created(view(id), secret);
    }

    /** A person's tokens, the newest first: live ones and those that ended within 30 days. */
    List<TokenView> mine(long accountId) {
        return views(API_TOKEN.ACCOUNT_ID.eq(accountId).and(recent()), 100);
    }

    /** Every live token of the site, for its owner. */
    List<TokenView> all() {
        return views(live(), 500);
    }

    /** The person revokes a token of theirs; a token already ended stays as it was. */
    @Transactional
    void revoke(Viewer viewer, long id) {
        if (!db.fetchExists(API_TOKEN, API_TOKEN.ID.eq(id).and(API_TOKEN.ACCOUNT_ID.eq(viewer.accountId())))) {
            throw UserFacingException.notFound("Такого токена немає.");
        }
        end(id, viewer.accountId());
    }

    /** The site owner revokes anyone's token; it goes into «Журнал дій». */
    @Transactional
    void revokeAny(Viewer owner, long id) {
        Record row = db.select(API_TOKEN.NAME, ACCOUNT.NICK, API_TOKEN.REVOKED_AT).from(API_TOKEN)
                .join(ACCOUNT).on(ACCOUNT.ID.eq(API_TOKEN.ACCOUNT_ID)).where(API_TOKEN.ID.eq(id)).fetchOne();
        if (row == null) {
            throw UserFacingException.notFound("Такого токена немає.");
        }
        if (row.get(API_TOKEN.REVOKED_AT) == null) {
            end(id, owner.accountId());
            audit.record(owner.accountId(), "api_token_revoked", "api_token", id,
                    Map.of("nick", row.get(ACCOUNT.NICK), "name", row.get(API_TOKEN.NAME)));
        }
    }

    private void end(long id, long by) {
        db.update(API_TOKEN).set(API_TOKEN.REVOKED_AT, now()).set(API_TOKEN.REVOKED_BY, by)
                .where(API_TOKEN.ID.eq(id), API_TOKEN.REVOKED_AT.isNull()).execute();
    }

    // ---- requests made with a token -----------------------------------------------------------

    @Override
    public Optional<TokenGrant> authenticate(String token, String address) {
        if (token == null || !token.startsWith(PREFIX) || token.length() > 100) {
            return Optional.empty();
        }
        Record row = db.select(API_TOKEN.ID, API_TOKEN.ACCOUNT_ID, API_TOKEN.SCOPES).from(API_TOKEN)
                .join(ACCOUNT).on(ACCOUNT.ID.eq(API_TOKEN.ACCOUNT_ID))
                .where(API_TOKEN.SECRET_HASH.eq(hash(token)).and(live()))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        long id = row.get(API_TOKEN.ID);
        long accountId = row.get(API_TOKEN.ACCOUNT_ID);
        String ip = address == null ? null : address.substring(0, Math.min(address.length(), 64));
        db.update(API_TOKEN).set(API_TOKEN.LAST_USED_AT, now()).set(API_TOKEN.LAST_USED_IP, ip)
                .where(API_TOKEN.ID.eq(id), API_TOKEN.LAST_USED_AT.isNull()
                        .or(API_TOKEN.LAST_USED_AT.lt(now().minus(TOUCH)))
                        .or(API_TOKEN.LAST_USED_IP.isDistinctFrom(ip)))
                .execute();
        return Optional.of(new TokenGrant(id, accountId, Set.copyOf(Arrays.asList(row.get(API_TOKEN.SCOPES))),
                UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal(accountId), null, List.of())));
    }

    @Override
    public String refusal(TokenGrant grant, String method, String path) {
        Optional<Scope> needed = TokenScopes.required(method, path);
        if (needed.isEmpty()) {
            return "Це можна зробити лише на сайті, не токеном.";
        }
        if (!grant.scopes().contains(needed.get().code())) {
            return "Токену бракує права «" + needed.get().code() + "».";
        }
        return null;
    }

    @Override
    public void completed(TokenGrant grant, String method, String path, String address, int status) {
        if (READS.contains(method)) {
            return;
        }
        String where = path.length() > 300 ? path.substring(0, 300) : path;
        String ip = address == null ? null : address.substring(0, Math.min(address.length(), 64));
        db.insertInto(API_TOKEN_WRITE).set(API_TOKEN_WRITE.TOKEN_ID, grant.tokenId()).set(API_TOKEN_WRITE.ACCOUNT_ID, grant.accountId())
                .set(API_TOKEN_WRITE.METHOD, method).set(API_TOKEN_WRITE.PATH, where).set(API_TOKEN_WRITE.STATUS, status)
                .set(API_TOKEN_WRITE.IP, ip).execute();
        // Administration with a token is in «Журнал дій» too, beside what the action itself records there.
        if (where.startsWith("/api/admin/")) {
            audit.record(grant.accountId(), "api_admin", "api_token", grant.tokenId(),
                    Map.of("method", method, "path", where, "status", status));
        }
    }

    // ---- reading ------------------------------------------------------------------------------

    private Condition live() {
        return API_TOKEN.REVOKED_AT.isNull().and(API_TOKEN.EXPIRES_AT.gt(now()));
    }

    private Condition recent() {
        OffsetDateTime month = now().minusDays(30);
        return live().or(API_TOKEN.REVOKED_AT.gt(month)).or(API_TOKEN.REVOKED_AT.isNull().and(API_TOKEN.EXPIRES_AT.gt(month)));
    }

    private TokenView view(long id) {
        return views(API_TOKEN.ID.eq(id), 1).getFirst();
    }

    private List<TokenView> views(Condition where, int limit) {
        OffsetDateTime now = now();
        return db.select(API_TOKEN.ID, API_TOKEN.NAME, API_TOKEN.HINT, API_TOKEN.SCOPES, API_TOKEN.CREATED_AT, API_TOKEN.EXPIRES_AT,
                        API_TOKEN.LAST_USED_AT, API_TOKEN.LAST_USED_IP, API_TOKEN.REVOKED_AT, ACCOUNT.NICK)
                .from(API_TOKEN).join(ACCOUNT).on(ACCOUNT.ID.eq(API_TOKEN.ACCOUNT_ID))
                .where(where)
                .orderBy(API_TOKEN.ID.desc())
                .limit(limit)
                .fetch(r -> new TokenView(r.get(API_TOKEN.ID), r.get(API_TOKEN.NAME), r.get(API_TOKEN.HINT),
                        List.of(r.get(API_TOKEN.SCOPES)), r.get(API_TOKEN.CREATED_AT), r.get(API_TOKEN.EXPIRES_AT),
                        r.get(API_TOKEN.LAST_USED_AT), r.get(API_TOKEN.LAST_USED_IP), r.get(API_TOKEN.REVOKED_AT),
                        r.get(API_TOKEN.REVOKED_AT) != null ? "revoked" : r.get(API_TOKEN.EXPIRES_AT).isAfter(now) ? "active" : "expired",
                        r.get(ACCOUNT.NICK)));
    }

    /** Hex SHA-256 of the whole token. */
    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
