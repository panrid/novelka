package space.panrid.novelka.access;

import static space.panrid.novelka.jooq.Tables.EDITION;

import java.util.Map;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Publication payloads are checked when queued, when read, and again before external delivery. */
@Component
public class PublicationNotices {
    private final DSLContext db;

    PublicationNotices(DSLContext db) {
        this.db = db;
    }

    public boolean allowed(long accountId, Map<String, Object> payload) {
        Object raw = payload.get("editionId");
        if (raw == null) {
            return true;
        }
        if (!(raw instanceof Number number)) {
            return false;
        }
        return db.fetchExists(EDITION, EDITION.ID.eq(number.longValue()), EDITION.HIDDEN_AT.isNull(),
                EDITION.DELETED_AT.isNull(), Visibility.readableBy(accountId, null));
    }

    public static Condition visible(Field<JSONB> payload, Field<Long> recipient) {
        Field<Long> editionId = DSL.field("({0}->>'editionId')::bigint", Long.class, payload);
        return editionId.isNull().or(DSL.exists(DSL.selectOne().from(EDITION)
                .where(EDITION.ID.eq(editionId), EDITION.DELETED_AT.isNull(), EDITION.HIDDEN_AT.isNull(),
                        Visibility.readableByAccount(EDITION.ID, recipient))));
    }
}
