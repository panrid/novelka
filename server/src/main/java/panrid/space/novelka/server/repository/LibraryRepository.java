package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LibraryRepository {
    private static final String FROM = " FROM library_entries l JOIN cards ON cards.id=l.novel_id";
    private static final String FILTER = """
             WHERE l.account_id=? AND (?='' OR l.status=?)
               AND (?='' OR cards.display_title ILIKE ? ESCAPE '\\' OR cards.display_author ILIKE ? ESCAPE '\\')
            """;
    private final JdbcSession jdbc;

    public LibraryRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public void set(String account, String novel, String status) throws Exception {
        if (status.isEmpty()) jdbc.exec("DELETE FROM library_entries WHERE account_id=? AND novel_id=?", account, novel);
        else jdbc.exec("""
                INSERT INTO library_entries(account_id,novel_id,status) VALUES(?,?,?)
                ON CONFLICT(account_id,novel_id) DO UPDATE SET status=excluded.status,updated_at=now()
                """, account, novel, status);
    }

    public String status(String account, String novel) throws Exception {
        if (account == null) return null;
        var rows = jdbc.rows("SELECT status FROM library_entries WHERE account_id=? AND novel_id=?", account, novel);
        return rows.isEmpty() ? null : (String) rows.getFirst().get("status");
    }

    public ListPage<Map<String, Object>> list(String account, String status, ListQuery query) throws Exception {
        Object[] filters = {account, status, status, query.q(), query.pattern(), query.pattern()};
        long total = ((Number) jdbc.rows(CatalogRepository.BASE + "SELECT count(*) total" + FROM + FILTER, filters)
                .getFirst().get("total")).longValue();
        String order = query.order(Map.of("updated", "l.updated_at", "title", "cards.display_title", "ready", "cards.ready_chapters"), "updated", "l.novel_id");
        var rows = jdbc.rows(CatalogRepository.BASE + "SELECT cards.id,cards.data,cards.ready_chapters,l.status,l.updated_at" + FROM + FILTER
                + order + " LIMIT ? OFFSET ?", account, status, status, query.q(), query.pattern(), query.pattern(), query.size(), query.offset());
        return ListPage.of(rows, query, total);
    }

    /** Number of novels on every shelf, including empty ones, in display order. */
    public Map<String, Long> counts(String account, Iterable<String> statuses) throws Exception {
        var result = new LinkedHashMap<String, Long>();
        for (var status : statuses) result.put(status, 0L);
        for (var row : jdbc.rows("SELECT status,count(*) AS total FROM library_entries WHERE account_id=? GROUP BY status", account))
            result.put((String) row.get("status"), ((Number) row.get("total")).longValue());
        return result;
    }
}
