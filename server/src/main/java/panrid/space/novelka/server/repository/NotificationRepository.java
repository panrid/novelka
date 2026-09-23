package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;

import java.util.Map;

public final class NotificationRepository {
    private final JdbcSession jdbc;
    private static final String VISIBLE = "n.created_at >= a.created_at AND (n.audience='READER' OR ?)";

    public NotificationRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> list(Account account, long before) throws Exception {
        boolean admin = account.role().includes(Role.ADMIN);
        var totals = jdbc.rows("SELECT count(*) FILTER(WHERE r.notification_id IS NULL) AS unread, COALESCE(max(n.id),0) AS latest_id"
                + " FROM notifications n JOIN accounts a ON a.id=?"
                + " LEFT JOIN notification_reads r ON r.notification_id=n.id AND r.account_id=a.id WHERE " + VISIBLE,
                account.id(), admin).getFirst();
        var items = jdbc.rows("""
                SELECT n.id,n.kind,n.novel_id,n.chapter,n.task_id,n.entry_count,n.created_at,
                    COALESCE(NULLIF(v.data->>'titleUk',''),v.data->>'title',n.novel_id) AS novel_title,
                    (r.notification_id IS NOT NULL) AS read,t.operation AS task_operation,
                    (t.request->>'first')::int AS task_first,(t.request->>'last')::int AS task_last,j.chapter AS task_job_chapter
                FROM notifications n JOIN accounts a ON a.id=?
                LEFT JOIN notification_reads r ON r.notification_id=n.id AND r.account_id=a.id
                LEFT JOIN novels v ON v.id=n.novel_id
                LEFT JOIN web_tasks t ON t.id=n.task_id LEFT JOIN jobs j ON j.id=t.request->>'jobId' WHERE
                """ + VISIBLE + " AND (?=0 OR n.id<?) ORDER BY n.id DESC LIMIT 31", account.id(), admin, before, before);
        boolean hasMore = items.size() > 30;
        if (hasMore) items = items.subList(0, 30);
        long nextCursor = hasMore ? ((Number) items.getLast().get("id")).longValue() : 0;
        return Map.of("items", items, "unread", totals.get("unread"), "latestId", totals.get("latest_id"), "nextCursor", nextCursor);
    }

    public boolean read(Account account, long id) throws Exception {
        var visible = jdbc.rows("SELECT n.id FROM notifications n JOIN accounts a ON a.id=? WHERE " + VISIBLE + " AND n.id=?",
                account.id(), account.role().includes(Role.ADMIN), id);
        if (visible.isEmpty()) return false;
        jdbc.exec("INSERT INTO notification_reads(account_id,notification_id) VALUES(?,?) ON CONFLICT DO NOTHING", account.id(), id);
        return true;
    }

    public void readThrough(Account account, long through) throws Exception {
        jdbc.exec("INSERT INTO notification_reads(account_id,notification_id) SELECT a.id,n.id"
                + " FROM notifications n JOIN accounts a ON a.id=? WHERE " + VISIBLE + " AND n.id<=? ON CONFLICT DO NOTHING",
                account.id(), account.role().includes(Role.ADMIN), through);
    }
}
