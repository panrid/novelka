package space.panrid.novelka.ledger.internal;

import static space.panrid.novelka.jooq.Tables.SHAH_BALANCE;
import static space.panrid.novelka.jooq.Tables.SHAH_ENTRY;
import static space.panrid.novelka.jooq.Tables.SHAH_HOLD;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.jooq.tables.records.ShahHoldRecord;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.ledger.ShahsGranted;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.audit.AuditLog;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.json.JsonMapper;

@Service
class LedgerService implements Ledger {

    static final String PRICE_KEY = "ledger.price";
    /** 7 cents of model cost per шаг (рішення 29). */
    static final long DEFAULT_MICRO_USD_PER_SHAH = 70_000;
    static final int MAX_GRANT = 100_000;

    private final DSLContext db;
    private final SiteSettings siteSettings;
    private final JsonMapper json;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    LedgerService(DSLContext db, SiteSettings siteSettings, JsonMapper json, AuditLog audit, ApplicationEventPublisher events,
            Clock clock) {
        this.db = db;
        this.siteSettings = siteSettings;
        this.json = json;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Balance balance(long accountId) {
        return db.select(SHAH_BALANCE.AVAILABLE, SHAH_BALANCE.RESERVED).from(SHAH_BALANCE).where(SHAH_BALANCE.ACCOUNT_ID.eq(accountId))
                .fetchOptional(row -> new Balance(row.value1(), row.value2())).orElse(new Balance(0, 0));
    }

    @Override
    public long microUsdPerShah() {
        return siteSettings.json(PRICE_KEY).map(value -> json.readTree(value).path("microUsdPerShah").asLong(DEFAULT_MICRO_USD_PER_SHAH))
                .filter(price -> price > 0).orElse(DEFAULT_MICRO_USD_PER_SHAH);
    }

    void savePrice(long microUsdPerShah, long ownerId) {
        if (microUsdPerShah < 10_000 || microUsdPerShah > 1_000_000) {
            throw UserFacingException.badRequest("Ціна шагу — від $0,01 до $1.");
        }
        siteSettings.put(PRICE_KEY, json.writeValueAsString(Map.of("microUsdPerShah", microUsdPerShah)), ownerId);
        audit.record(ownerId, "shah_price", "site", null, Map.of("microUsdPerShah", microUsdPerShah));
    }

    @Override
    public int shahOf(long microUsd) {
        if (microUsd <= 0) {
            return 0;
        }
        long price = microUsdPerShah();
        return (int) ((microUsd + price - 1) / price);
    }

    @Override
    @Transactional
    public long hold(long accountId, int shah, String what) {
        if (shah <= 0) {
            throw new IllegalArgumentException("A hold is at least one шаг");
        }
        ensureBalance(accountId);
        int changed = db.update(SHAH_BALANCE)
                .set(SHAH_BALANCE.AVAILABLE, SHAH_BALANCE.AVAILABLE.minus(shah))
                .set(SHAH_BALANCE.RESERVED, SHAH_BALANCE.RESERVED.plus(shah))
                .set(SHAH_BALANCE.UPDATED_AT, now())
                .where(SHAH_BALANCE.ACCOUNT_ID.eq(accountId), SHAH_BALANCE.AVAILABLE.ge(shah))
                .execute();
        if (changed == 0) {
            throw UserFacingException.badRequest("Не вистачає шагів: потрібно %d, у вас %d.".formatted(shah, balance(accountId).available()));
        }
        long holdId = db.insertInto(SHAH_HOLD)
                .set(SHAH_HOLD.ACCOUNT_ID, accountId)
                .set(SHAH_HOLD.AMOUNT, shah)
                .set(SHAH_HOLD.WHAT, what)
                .returning(SHAH_HOLD.ID).fetchSingle().getId();
        entry(accountId, "hold", shah, holdId, what, null);
        return holdId;
    }

    @Override
    @Transactional
    public int settle(long holdId, long spentMicroUsd) {
        ShahHoldRecord hold = db.update(SHAH_HOLD).set(SHAH_HOLD.SETTLED_AT, now())
                .where(SHAH_HOLD.ID.eq(holdId), SHAH_HOLD.SETTLED_AT.isNull())
                .returning().fetchOne();
        if (hold == null) {
            Integer charged = db.select(SHAH_HOLD.CHARGED).from(SHAH_HOLD).where(SHAH_HOLD.ID.eq(holdId)).fetchOne(SHAH_HOLD.CHARGED);
            return charged == null ? 0 : charged;
        }
        int charge = Math.min(hold.getAmount(), shahOf(spentMicroUsd));
        int back = hold.getAmount() - charge;
        db.update(SHAH_HOLD).set(SHAH_HOLD.CHARGED, charge).where(SHAH_HOLD.ID.eq(holdId)).execute();
        db.update(SHAH_BALANCE)
                .set(SHAH_BALANCE.RESERVED, SHAH_BALANCE.RESERVED.minus(hold.getAmount()))
                .set(SHAH_BALANCE.AVAILABLE, SHAH_BALANCE.AVAILABLE.plus(back))
                .set(SHAH_BALANCE.UPDATED_AT, now())
                .where(SHAH_BALANCE.ACCOUNT_ID.eq(hold.getAccountId()))
                .execute();
        if (charge > 0) {
            entry(hold.getAccountId(), "charge", charge, holdId, hold.getWhat(), null);
        }
        if (back > 0) {
            entry(hold.getAccountId(), "release", back, holdId, hold.getWhat(), null);
        }
        return charge;
    }

    /** The site owner gives шаги; they are never taken back (рішення 29). */
    @Transactional
    void grant(long accountId, int shah, String note, long ownerId) {
        if (shah < 1 || shah > MAX_GRANT) {
            throw UserFacingException.badRequest("Нарахувати можна від 1 до %d шагів.".formatted(MAX_GRANT));
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > 300) {
            throw UserFacingException.badRequest("Примітка — до 300 знаків.");
        }
        ensureBalance(accountId);
        db.update(SHAH_BALANCE)
                .set(SHAH_BALANCE.AVAILABLE, SHAH_BALANCE.AVAILABLE.plus(shah))
                .set(SHAH_BALANCE.UPDATED_AT, now())
                .where(SHAH_BALANCE.ACCOUNT_ID.eq(accountId))
                .execute();
        entry(accountId, "grant", shah, null, text, ownerId);
        audit.record(ownerId, "shahs_granted", "account", accountId, text == null ? Map.of("shah", shah) : Map.of("shah", shah, "note", text));
        events.publishEvent(new ShahsGranted(accountId, shah, text, ownerId));
    }

    record Entry(String kind, int amount, String what, OffsetDateTime createdAt) {
    }

    record OpenHold(int amount, String what, OffsetDateTime createdAt) {
    }

    static final int PAGE = 30;

    /** Grants and charges, newest first; holds and returns are shown as the runs still going. */
    List<Entry> history(long accountId, int page) {
        return db.selectFrom(SHAH_ENTRY)
                .where(SHAH_ENTRY.ACCOUNT_ID.eq(accountId), SHAH_ENTRY.KIND.in("grant", "charge"))
                .orderBy(SHAH_ENTRY.ID.desc())
                .limit(PAGE + 1).offset((Math.max(1, page) - 1) * PAGE)
                .fetch(row -> new Entry(row.getKind(), row.getAmount(), row.getWhat(), row.getCreatedAt()));
    }

    List<OpenHold> openHolds(long accountId) {
        return db.selectFrom(SHAH_HOLD).where(SHAH_HOLD.ACCOUNT_ID.eq(accountId), SHAH_HOLD.SETTLED_AT.isNull())
                .orderBy(SHAH_HOLD.ID.desc())
                .fetch(row -> new OpenHold(row.getAmount(), row.getWhat(), row.getCreatedAt()));
    }

    private void ensureBalance(long accountId) {
        db.insertInto(SHAH_BALANCE).set(SHAH_BALANCE.ACCOUNT_ID, accountId).onConflictDoNothing().execute();
    }

    private void entry(long accountId, String kind, int amount, Long holdId, String what, Long actorId) {
        db.insertInto(SHAH_ENTRY)
                .set(SHAH_ENTRY.ACCOUNT_ID, accountId)
                .set(SHAH_ENTRY.KIND, kind)
                .set(SHAH_ENTRY.AMOUNT, amount)
                .set(SHAH_ENTRY.HOLD_ID, holdId)
                .set(SHAH_ENTRY.WHAT, what)
                .set(SHAH_ENTRY.ACTOR_ID, actorId)
                .execute();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
