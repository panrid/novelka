package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

import java.util.List;
import java.util.Map;

public final class ChatRepository {
    private static final String SELECT = """
            SELECT m.id,m.author_id,a.username AS author,m.body,m.created_at FROM chat_messages m
            JOIN accounts a ON a.id=m.author_id WHERE m.deleted_at IS NULL
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

    public Map<String, Object> get(long id) throws Exception {
        var rows = jdbc.rows("SELECT id,author_id,deleted_at FROM chat_messages WHERE id=?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean recentlyPosted(String author) throws Exception {
        return !jdbc.rows("SELECT 1 FROM chat_messages WHERE author_id=? AND created_at>now()-interval '2 seconds'", author).isEmpty();
    }

    public long create(String author, String body) throws Exception {
        return ((Number) jdbc.rows("INSERT INTO chat_messages(author_id,body) VALUES(?,?) RETURNING id", author, body).getFirst().get("id")).longValue();
    }

    public void delete(long id, String actor) throws Exception {
        jdbc.exec("UPDATE chat_messages SET deleted_at=now(),deleted_by=? WHERE id=? AND deleted_at IS NULL", actor, id);
    }
}
