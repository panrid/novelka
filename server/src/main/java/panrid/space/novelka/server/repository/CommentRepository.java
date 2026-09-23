package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

import java.util.List;
import java.util.Map;

public final class CommentRepository {
    private final JdbcSession jdbc;

    public CommentRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    /** Newest first; the cursor is the smallest id already shown. Author names are read live from accounts. */
    public List<Map<String, Object>> page(String novel, int chapter, long before, int limit) throws Exception {
        return jdbc.rows("""
                SELECT c.id,c.author_id,a.username AS author,c.body,c.created_at,c.edited_at FROM comments c
                JOIN accounts a ON a.id=c.author_id
                WHERE c.novel_id=? AND c.chapter=? AND c.deleted_at IS NULL AND (?=0 OR c.id<?)
                ORDER BY c.id DESC LIMIT ?
                """, novel, chapter, before, before, limit);
    }

    public Map<String, Object> get(long id) throws Exception {
        var rows = jdbc.rows("SELECT id,novel_id,chapter,author_id,deleted_at FROM comments WHERE id=?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long create(String novel, int chapter, String author, String body) throws Exception {
        return ((Number) jdbc.rows("INSERT INTO comments(novel_id,chapter,author_id,body) VALUES(?,?,?,?) RETURNING id",
                novel, chapter, author, body).getFirst().get("id")).longValue();
    }

    public boolean recentlyPosted(String author) throws Exception {
        return !jdbc.rows("SELECT 1 FROM comments WHERE author_id=? AND created_at>now()-interval '5 seconds'", author).isEmpty();
    }

    public void edit(long id, String body) throws Exception {
        jdbc.exec("UPDATE comments SET body=?,edited_at=now() WHERE id=? AND deleted_at IS NULL", body, id);
    }

    public void delete(long id, String actor) throws Exception {
        jdbc.exec("UPDATE comments SET deleted_at=now(),deleted_by=? WHERE id=? AND deleted_at IS NULL", actor, id);
    }
}
