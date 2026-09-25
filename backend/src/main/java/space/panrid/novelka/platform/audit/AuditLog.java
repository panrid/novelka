package space.panrid.novelka.platform.audit;

import static space.panrid.novelka.jooq.Tables.AUDIT_LOG;

import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Every moderator, administrator and owner action, for «Журнал дій». Written in the action's transaction. */
@Component
public class AuditLog {

    private final DSLContext db;
    private final JsonMapper json;

    AuditLog(DSLContext db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    public void record(long actorId, String action, String targetType, Long targetId, Map<String, ?> details) {
        db.insertInto(AUDIT_LOG).set(AUDIT_LOG.ACTOR_ID, actorId).set(AUDIT_LOG.ACTION, action)
                .set(AUDIT_LOG.TARGET_TYPE, targetType).set(AUDIT_LOG.TARGET_ID, targetId)
                .set(AUDIT_LOG.DETAILS, JSONB.valueOf(json.writeValueAsString(details))).execute();
    }
}
