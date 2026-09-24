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
                SELECT c.id,c.author_id,a.username AS author,c.body,c.created_at,c.edited_at,
                    (c.hidden_at IS NOT NULL) AS hidden,c.hidden_reason,c.reply_to,pa.username AS reply_author,
                    CASE WHEN p.deleted_at IS NULL THEN left(p.body,200) END AS reply_body,
                    (p.hidden_at IS NOT NULL) AS reply_hidden,(p.deleted_at IS NOT NULL) AS reply_deleted FROM comments c
                JOIN accounts a ON a.id=c.author_id
                LEFT JOIN comments p ON p.id=c.reply_to LEFT JOIN accounts pa ON pa.id=p.author_id
                WHERE c.novel_id=? AND c.chapter=? AND c.deleted_at IS NULL AND (?=0 OR c.id<?)
                ORDER BY c.id DESC LIMIT ?
                """, novel, chapter, before, before, limit);
    }

    public Map<String, Object> get(long id) throws Exception {
        var rows = jdbc.rows("SELECT id,novel_id,chapter,author_id,deleted_at FROM comments WHERE id=?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long create(String novel, int chapter, String author, String body, Long replyTo) throws Exception {
        return ((Number) jdbc.rows("INSERT INTO comments(novel_id,chapter,author_id,body,reply_to) VALUES(?,?,?,?,?) RETURNING id",
                novel, chapter, author, body, replyTo).getFirst().get("id")).longValue();
    }

    public boolean recentlyPosted(String author) throws Exception {
        return !jdbc.rows("SELECT 1 FROM comments WHERE author_id=? AND created_at>now()-interval '5 seconds'", author).isEmpty();
    }

    public void edit(long id, String body) throws Exception {
        jdbc.exec("UPDATE comments SET body=?,edited_at=now() WHERE id=? AND deleted_at IS NULL", body, id);
    }

    /** Hides ({@code hidden=true}) or restores a comment; the text stays, readers can reveal it for themselves. */
    public void hide(long id, String actor, boolean hidden, String reason) throws Exception {
        jdbc.exec("UPDATE comments SET hidden_at=CASE WHEN ? THEN now() END,hidden_by=CASE WHEN ? THEN ? END,hidden_reason=?"
                + " WHERE id=? AND deleted_at IS NULL", hidden, hidden, actor, hidden ? reason : "", id);
    }

    public void delete(long id, String actor) throws Exception {
        jdbc.exec("UPDATE comments SET deleted_at=now(),deleted_by=? WHERE id=? AND deleted_at IS NULL", actor, id);
    }
}
