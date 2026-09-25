package space.panrid.novelka.messaging.internal;

import static space.panrid.novelka.jooq.Tables.MESSAGE;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.messaging.MessageModeration;

@Service
class MessageModerationService implements MessageModeration {

    private final DSLContext db;

    MessageModerationService(DSLContext db) {
        this.db = db;
    }

    @Override
    @Transactional
    public void hide(long messageId, long moderatorId, String reason) {
        db.update(MESSAGE).set(MESSAGE.HIDDEN_AT, DSL.currentOffsetDateTime()).set(MESSAGE.HIDDEN_BY, moderatorId)
                .set(MESSAGE.HIDDEN_REASON, reason).where(MESSAGE.ID.eq(messageId), MESSAGE.HIDDEN_AT.isNull()).execute();
    }

    @Override
    @Transactional
    public void restore(long messageId) {
        db.update(MESSAGE).setNull(MESSAGE.HIDDEN_AT).setNull(MESSAGE.HIDDEN_BY).setNull(MESSAGE.HIDDEN_REASON)
                .where(MESSAGE.ID.eq(messageId)).execute();
    }
}
