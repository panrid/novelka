package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

import java.util.List;
import java.util.Map;

public final class ChatRepository {
    private static final String SELECT = """
            SELECT m.id,m.author_id,a.username AS author,m.body,m.created_at,(m.hidden_at IS NOT NULL) AS hidden,m.hidden_reason,
                m.reply_to,pa.username AS reply_author,CASE WHEN p.deleted_at IS NULL THEN left(p.body,200) END AS reply_body,
                (p.hidden_at IS NOT NULL) AS reply_hidden,(p.deleted_at IS NOT NULL) AS reply_deleted FROM chat_messages m
            JOIN accounts a ON a.id=m.author_id LEFT JOIN chat_messages p ON p.id=m.reply_to LEFT JOIN accounts pa ON pa.id=p.author_id
            WHERE m.deleted_at IS NULL
            """;
    private final JdbcSession jdbc;

    public ChatRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    /** Newest first; {@code before=0} opens the latest page. */
    public List<Map<String, Object>> history(long before, int limit) throws Exception {
        return jdbc.rows(SELECT + " AND (?=0 OR m.id<?) ORDER BY m.id DESC LIMIT ?", before, before, limit);
    }

    /** Messages newer than the client's last id, oldest first, for polling. */
    public List<Map<String, Object>> after(long after, int limit) throws Exception {
        return jdbc.rows(SELECT + " AND m.id>? ORDER BY m.id LIMIT ?", after, limit);
    }

    /** Recently removed ids, so open chats drop them without a reload. */
    public List<Long> recentlyDeleted() throws Exception {
        return jdbc.rows("SELECT id FROM chat_messages WHERE deleted_at>now()-interval '15 minutes' ORDER BY id").stream()
                .map(row -> ((Number) row.get("id")).longValue()).toList();
    }

    /** Messages hidden or restored recently, with their current state, so open chats update them in place. */
    public List<Map<String, Object>> recentlyModerated() throws Exception {
        return jdbc.rows(SELECT + " AND m.moderated_at>now()-interval '15 minutes' ORDER BY m.id");
    }

    public void hide(long id, String actor, boolean hidden, String reason) throws Exception {
        jdbc.exec("UPDATE chat_messages SET hidden_at=CASE WHEN ? THEN now() END,hidden_by=CASE WHEN ? THEN ? END,hidden_reason=?,"
                + "moderated_at=now() WHERE id=? AND deleted_at IS NULL", hidden, hidden, actor, hidden ? reason : "", id);
    }

    public Map<String, Object> get(long id) throws Exception {
        var rows = jdbc.rows("SELECT id,author_id,deleted_at FROM chat_messages WHERE id=?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean recentlyPosted(String author) throws Exception {
        return !jdbc.rows("SELECT 1 FROM chat_messages WHERE author_id=? AND created_at>now()-interval '2 seconds'", author).isEmpty();
    }

    public long create(String author, String body, Long replyTo) throws Exception {
        return ((Number) jdbc.rows("INSERT INTO chat_messages(author_id,body,reply_to) VALUES(?,?,?) RETURNING id", author, body, replyTo)
                .getFirst().get("id")).longValue();
    }

    public void delete(long id, String actor) throws Exception {
        jdbc.exec("UPDATE chat_messages SET deleted_at=now(),deleted_by=? WHERE id=? AND deleted_at IS NULL", actor, id);
    }
}
