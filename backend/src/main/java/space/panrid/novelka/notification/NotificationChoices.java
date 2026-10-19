package space.panrid.novelka.notification;

import static space.panrid.novelka.jooq.Tables.NOTIFICATION_CHOICE;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.jooq.tables.records.NotificationChoiceRecord;

/**
 * Where each topic of notifications goes: «Сповіщення» in the settings, a table of topics
 * against «На сайті» and «Telegram». Nothing chosen means both.
 */
@Component
public class NotificationChoices {

    /** The rows of the table; a notification's kind belongs to exactly one. */
    public enum Topic {
        CHAPTERS, REPLIES, MESSAGES, EDITS, SOURCE, OTHER;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Topic fromCode(String code) {
            return Arrays.stream(values()).filter(topic -> topic.code().equals(code)).findFirst().orElse(null);
        }

        /** The topic a notification of this kind belongs to. */
        public static Topic of(String kind) {
            return switch (kind) {
                case "new_chapters" -> CHAPTERS;
                case "reply", "mention", "team_mention" -> REPLIES;
                case "suggestions_submitted", "suggestions_reviewed" -> EDITS;
                case "source_chapters" -> SOURCE;
                default -> OTHER;
            };
        }
    }

    /** One row of the table. Messages are always on the site: «Повідомлення» is where they live. */
    public record Choice(String topic, boolean site, boolean telegram) {
    }

    private final DSLContext db;

    NotificationChoices(DSLContext db) {
        this.db = db;
    }

    public List<Choice> all(long accountId) {
        Map<String, NotificationChoiceRecord> chosen = db.selectFrom(NOTIFICATION_CHOICE)
                .where(NOTIFICATION_CHOICE.ACCOUNT_ID.eq(accountId)).fetchMap(NOTIFICATION_CHOICE.TOPIC);
        return Arrays.stream(Topic.values()).map(topic -> {
            NotificationChoiceRecord row = chosen.get(topic.code());
            return new Choice(topic.code(), topic == Topic.MESSAGES || row == null || row.getOnSite(), row == null || row.getInTelegram());
        }).toList();
    }

    public boolean onSite(long accountId, Topic topic) {
        if (topic == Topic.MESSAGES) {
            return true;
        }
        Boolean value = db.select(NOTIFICATION_CHOICE.ON_SITE).from(NOTIFICATION_CHOICE)
                .where(NOTIFICATION_CHOICE.ACCOUNT_ID.eq(accountId), NOTIFICATION_CHOICE.TOPIC.eq(topic.code()))
                .fetchOne(NOTIFICATION_CHOICE.ON_SITE);
        return value == null || value;
    }

    public boolean inTelegram(long accountId, Topic topic) {
        Boolean value = db.select(NOTIFICATION_CHOICE.IN_TELEGRAM).from(NOTIFICATION_CHOICE)
                .where(NOTIFICATION_CHOICE.ACCOUNT_ID.eq(accountId), NOTIFICATION_CHOICE.TOPIC.eq(topic.code()))
                .fetchOne(NOTIFICATION_CHOICE.IN_TELEGRAM);
        return value == null || value;
    }

    /** Of {@code accountIds}, those who turned the topic off on the site: one query for a whole batch. */
    public Set<Long> offSite(Collection<Long> accountIds, Topic topic) {
        if (topic == Topic.MESSAGES || accountIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(db.select(NOTIFICATION_CHOICE.ACCOUNT_ID).from(NOTIFICATION_CHOICE)
                .where(NOTIFICATION_CHOICE.ACCOUNT_ID.in(accountIds), NOTIFICATION_CHOICE.TOPIC.eq(topic.code()),
                        NOTIFICATION_CHOICE.ON_SITE.isFalse())
                .fetch(NOTIFICATION_CHOICE.ACCOUNT_ID));
    }

    /** Of {@code accountIds}, those who turned the topic off in Telegram. */
    public Set<Long> offTelegram(Collection<Long> accountIds, Topic topic) {
        if (accountIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(db.select(NOTIFICATION_CHOICE.ACCOUNT_ID).from(NOTIFICATION_CHOICE)
                .where(NOTIFICATION_CHOICE.ACCOUNT_ID.in(accountIds), NOTIFICATION_CHOICE.TOPIC.eq(topic.code()),
                        NOTIFICATION_CHOICE.IN_TELEGRAM.isFalse())
                .fetch(NOTIFICATION_CHOICE.ACCOUNT_ID));
    }

    /** Changes what is given; {@code null} keeps it. */
    @Transactional
    public void set(long accountId, Topic topic, Boolean site, Boolean telegram) {
        db.insertInto(NOTIFICATION_CHOICE)
                .set(NOTIFICATION_CHOICE.ACCOUNT_ID, accountId)
                .set(NOTIFICATION_CHOICE.TOPIC, topic.code())
                .set(NOTIFICATION_CHOICE.ON_SITE, site == null || site)
                .set(NOTIFICATION_CHOICE.IN_TELEGRAM, telegram == null || telegram)
                .onConflict(NOTIFICATION_CHOICE.ACCOUNT_ID, NOTIFICATION_CHOICE.TOPIC).doUpdate()
                .set(NOTIFICATION_CHOICE.ON_SITE, site == null ? NOTIFICATION_CHOICE.ON_SITE : org.jooq.impl.DSL.val(site))
                .set(NOTIFICATION_CHOICE.IN_TELEGRAM, telegram == null ? NOTIFICATION_CHOICE.IN_TELEGRAM : org.jooq.impl.DSL.val(telegram))
                .execute();
    }
}
