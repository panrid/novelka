package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.List;
import java.util.Map;

public final class AuditRepository {
    private final JdbcSession jdbc;

    public AuditRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public void add(String actor, String action, String target, Object details) throws Exception {
        jdbc.exec("INSERT INTO audit_events(actor_id,action,target,details) VALUES(?,?,?,?::jsonb)",
                actor, action, target, Json.write(details));
    }

    public List<Map<String, Object>> list(int offset) throws Exception {
        return jdbc.rows("SELECT a.id,u.username,a.action,a.target,a.details,a.created_at FROM audit_events a"
                + " LEFT JOIN accounts u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT 50 OFFSET ?", offset);
    }
}
