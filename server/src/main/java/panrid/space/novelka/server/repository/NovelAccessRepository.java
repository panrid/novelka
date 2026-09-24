package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

import java.util.List;
import java.util.Map;

/** Per-novel rights: the translator (owner), the editors they picked and whether review is open to everyone. */
public final class NovelAccessRepository {
    /** SQL condition "the account may review corrections of the novel in column {@code %s}"; binds the account id twice. */
    public static final String REVIEWABLE = "EXISTS (SELECT 1 FROM novels rn WHERE rn.id=%s AND (rn.owner_id=? OR rn.open_review"
            + " OR EXISTS (SELECT 1 FROM novel_editors re WHERE re.novel_id=rn.id AND re.account_id=?)))";
    private final JdbcSession jdbc;

    public NovelAccessRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    /** owner_id (nullable) and open_review of an existing novel. */
    public Map<String, Object> novel(String novel) throws Exception {
        return jdbc.rows("SELECT n.owner_id,n.open_review,a.username AS owner_name,(n.hidden_at IS NOT NULL) AS hidden,n.hidden_reason"
                + " FROM novels n LEFT JOIN accounts a ON a.id=n.owner_id"
                + " WHERE n.id=?", novel).getFirst();
    }

    /** Novels the account translates, or every novel for {@code account == null} (administrators), by title or ID. */
    public List<Map<String, Object>> manageable(String account, String q, int limit) throws Exception {
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.rows("SELECT id,COALESCE(NULLIF(data->>'titleUk',''),data->>'title') AS title,(hidden_at IS NOT NULL) AS hidden FROM novels"
                + " WHERE (?::text IS NULL OR owner_id=?) AND (?='' OR id ILIKE ? ESCAPE '\\'"
                + " OR COALESCE(NULLIF(data->>'titleUk',''),data->>'title') ILIKE ? ESCAPE '\\')"
                + " ORDER BY lower(COALESCE(NULLIF(data->>'titleUk',''),data->>'title')),id LIMIT ?", account, account, q, pattern, pattern, limit);
    }

    public boolean isEditor(String novel, String account) throws Exception {
        return !jdbc.rows("SELECT 1 FROM novel_editors WHERE novel_id=? AND account_id=?", novel, account).isEmpty();
    }

    public boolean reviewsAnything(String account) throws Exception {
        return !jdbc.rows("SELECT 1 FROM novels WHERE owner_id=? OR open_review UNION ALL SELECT 1 FROM novel_editors WHERE account_id=? LIMIT 1",
                account, account).isEmpty();
    }

    public List<Map<String, Object>> editors(String novel) throws Exception {
        return jdbc.rows("SELECT e.account_id AS id,a.username,e.granted_at FROM novel_editors e JOIN accounts a ON a.id=e.account_id"
                + " WHERE e.novel_id=? ORDER BY lower(a.username),a.id", novel);
    }

    public boolean addEditor(String novel, String account, String grantedBy) throws Exception {
        return !jdbc.rows("INSERT INTO novel_editors(novel_id,account_id,granted_by) VALUES(?,?,?) ON CONFLICT DO NOTHING RETURNING account_id",
                novel, account, grantedBy).isEmpty();
    }

    public boolean removeEditor(String novel, String account) throws Exception {
        return !jdbc.rows("DELETE FROM novel_editors WHERE novel_id=? AND account_id=? RETURNING account_id", novel, account).isEmpty();
    }

    public void openReview(String novel, boolean open) throws Exception {
        jdbc.exec("UPDATE novels SET open_review=? WHERE id=?", open, novel);
    }

    public void hide(String novel, String actor, boolean hidden, String reason) throws Exception {
        jdbc.exec("UPDATE novels SET hidden_at=CASE WHEN ? THEN now() END,hidden_by=CASE WHEN ? THEN ? END,hidden_reason=? WHERE id=?",
                hidden, hidden, actor, hidden ? reason : "", novel);
    }

    public void owner(String novel, String account) throws Exception {
        jdbc.exec("UPDATE novels SET owner_id=? WHERE id=?", account, novel);
    }
}
