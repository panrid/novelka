package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.List;
import java.util.Map;

public final class AuditRepository {
    private final JdbcSession jdbc;

    public AuditRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public void add(String actor, String action, String target, Object details) throws Exception {
        jdbc.exec("INSERT INTO audit_events(actor_id,action,target,details) VALUES(?,?,?,?::jsonb)",
                actor, action, target, Json.write(details));
    }

    public ListPage<Map<String, Object>> list(ListQuery query, String action) throws Exception {
        String where = " WHERE (?='' OR COALESCE(u.username,'') ILIKE ? ESCAPE '\\' OR a.target ILIKE ? ESCAPE '\\')"
                + " AND (?='' OR a.action=?)";
        String from = " FROM audit_events a LEFT JOIN accounts u ON u.id=a.actor_id";
        long total = ((Number) jdbc.rows("SELECT count(*) total" + from + where,
                query.q(), query.pattern(), query.pattern(), action, action).getFirst().get("total")).longValue();
        String order = query.order(Map.of("created", "a.created_at", "actor", "u.username", "action", "a.action"), "created", "a.id");
        var items = jdbc.rows("SELECT a.id,u.username,a.action,a.target,a.details,a.created_at" + from + where + order + " LIMIT ? OFFSET ?",
                query.q(), query.pattern(), query.pattern(), action, action, query.size(), query.offset());
        return ListPage.of(items, query, total);
    }
}
