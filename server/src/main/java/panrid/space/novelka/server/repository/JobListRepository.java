package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.List;
import java.util.Map;

public final class JobListRepository {
    private final JdbcSession jdbc;

    public JobListRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public ListPage<Map<String, Object>> list(String novel, ListQuery query, String state) throws Exception {
        if (!state.isEmpty() && !List.of("pending", "running", "complete", "needs-review", "failed").contains(state))
            throw new IllegalArgumentException("Невідомий стан перекладу.");
        String where = " WHERE novel_id=? AND (?='' OR state=?) AND (?='' OR id ILIKE ? ESCAPE '\\' OR chapter::text LIKE ? ESCAPE '\\')";
        long total = ((Number) jdbc.rows("SELECT count(*) total FROM jobs" + where,
                novel, state, state, query.q(), query.pattern(), query.pattern()).getFirst().get("total")).longValue();
        String order = query.order(Map.of("chapter", "chapter", "revision", "revision", "state", "state", "updated", "updated_at"), "updated", "id");
        var rows = jdbc.rows("SELECT id,chapter,revision,state,updated_at FROM jobs" + where + order + " LIMIT ? OFFSET ?",
                novel, state, state, query.q(), query.pattern(), query.pattern(), query.size(), query.offset());
        return ListPage.of(rows, query, total);
    }
}
