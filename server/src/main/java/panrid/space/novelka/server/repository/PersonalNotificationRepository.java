package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

/** Notifications addressed to one account: somebody mentioned them or replied to them. */
public final class PersonalNotificationRepository {
    private final JdbcSession jdbc;

    public PersonalNotificationRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    /** The key makes it idempotent: editing a comment never notifies the same person twice. */
    public void comment(String kind, String account, String actor, long comment, String novel, int chapter) throws Exception {
        jdbc.exec("INSERT INTO notifications(event_key,kind,audience,account_id,actor_id,comment_id,novel_id,chapter)"
                + " VALUES(?,?,'PERSONAL',?,?,?,?,?) ON CONFLICT(event_key) DO NOTHING",
                kind + ":comment:" + comment + ":" + account, kind, account, actor, comment, novel, chapter);
    }

    public void chat(String kind, String account, String actor, long message) throws Exception {
        jdbc.exec("INSERT INTO notifications(event_key,kind,audience,account_id,actor_id,chat_id)"
                + " VALUES(?,?,'PERSONAL',?,?,?) ON CONFLICT(event_key) DO NOTHING",
                kind + ":chat:" + message + ":" + account, kind, account, actor, message);
    }
}
