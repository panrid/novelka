package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

import java.util.List;
import java.util.Map;

public final class ManualDraftRepository {
    private final JdbcSession jdbc;

    public ManualDraftRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> draft(String novel, int chapter) throws Exception {
        var rows = jdbc.rows("SELECT chapter,title,text,updated_at FROM manual_drafts WHERE novel_id=? AND chapter=?", novel, chapter);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> drafts(String novel) throws Exception {
        return jdbc.rows("""
                SELECT d.chapter,d.title,d.updated_at,a.username AS updated_by FROM manual_drafts d
                JOIN accounts a ON a.id=d.updated_by WHERE d.novel_id=? ORDER BY d.chapter
                """, novel);
    }

    public void save(String novel, int chapter, String title, String text, String actor) throws Exception {
        jdbc.exec("""
                INSERT INTO manual_drafts(novel_id,chapter,title,text,updated_by) VALUES(?,?,?,?,?)
                ON CONFLICT(novel_id,chapter) DO UPDATE SET title=excluded.title,text=excluded.text,
                    updated_by=excluded.updated_by,updated_at=now()
                """, novel, chapter, title, text, actor);
    }

    public boolean delete(String novel, int chapter) throws Exception {
        boolean existed = draft(novel, chapter) != null;
        jdbc.exec("DELETE FROM manual_drafts WHERE novel_id=? AND chapter=?", novel, chapter);
        return existed;
    }
}
