package panrid.space.novelka.core.repository;

import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NovelRepository {
    private final JdbcSession jdbc;

    public NovelRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Novel n) throws Exception {
        jdbc.transaction(() -> {
            // IDs and aliases share one namespace; serialize both kinds of registration.
            jdbc.exec("SELECT pg_advisory_xact_lock(728616)");
            var aliases = jdbc.rows("SELECT novel_id FROM novel_aliases WHERE alias=?", n.id().toLowerCase(Locale.ROOT));
            if (!aliases.isEmpty() && !aliases.getFirst().get("novel_id").equals(n.id())) {
                throw new IllegalArgumentException("Novel ID conflicts with existing alias: " + n.id());
            }
            jdbc.exec(
                    "INSERT INTO novels(id,data) VALUES(?,?::jsonb) ON CONFLICT(id) DO UPDATE SET data=excluded.data",
                    n.id(),
                    Json.write(n));
            return null;
        });
    }

    public Novel novel(String id) throws Exception {
        return jdbc.one("SELECT data FROM novels WHERE id=?", Novel.class, id);
    }

    public String resolveNovel(String reference) throws Exception {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Novel ID or alias is required");
        }
        String value = reference.trim();
        if (!jdbc.rows("SELECT id FROM novels WHERE id=?", value).isEmpty()) return value;
        String alias = normalizeAlias(value);
        var matches = jdbc.rows("SELECT novel_id FROM novel_aliases WHERE alias=?", alias);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Novel or alias not found: " + reference);
        }
        return matches.getFirst().get("novel_id").toString();
    }

    public void saveAlias(String novel, String value) throws Exception {
        String alias = normalizeAlias(value);
        jdbc.transaction(() -> {
            jdbc.exec("SELECT pg_advisory_xact_lock(728616)");
            novel(novel);
            var idCollision = jdbc.rows("SELECT id FROM novels WHERE lower(id)=?", alias);
            if (idCollision.stream().anyMatch(row -> !row.get("id").equals(novel))) {
                throw new IllegalArgumentException("Alias conflicts with novel ID: " + alias);
            }
            var existing = jdbc.rows("SELECT novel_id FROM novel_aliases WHERE alias=?", alias);
            if (!existing.isEmpty()) {
                String target = existing.getFirst().get("novel_id").toString();
                if (target.equals(novel)) return null;
                throw new IllegalArgumentException("Alias already points to " + target + ": " + alias);
            }
            jdbc.exec("INSERT INTO novel_aliases(alias,novel_id) VALUES(?,?)", alias, novel);
            return null;
        });
    }

    public boolean removeAlias(String value) throws Exception {
        return !jdbc.rows("DELETE FROM novel_aliases WHERE alias=? RETURNING alias", normalizeAlias(value))
                .isEmpty();
    }

    public List<Map<String, Object>> aliases(String novel) throws Exception {
        return novel == null
                ? jdbc.rows("SELECT alias,novel_id FROM novel_aliases ORDER BY alias")
                : jdbc.rows("SELECT alias,novel_id FROM novel_aliases WHERE novel_id=? ORDER BY alias", novel);
    }

    public static String normalizeAlias(String value) {
        if (value == null) throw new IllegalArgumentException("Alias is required");
        String alias = value.strip().toLowerCase(Locale.ROOT);
        if (!alias.matches("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]{0,63}")) {
            throw new IllegalArgumentException(
                    "Alias must be 1-64 letters or digits and may contain '.', '_' or '-'");
        }
        return alias;
    }

    public List<Map<String, Object>> list() throws Exception {
        return jdbc.rows("SELECT n.id,n.data->>'title' title,"
                + "COALESCE(jsonb_agg(a.alias ORDER BY a.alias) FILTER (WHERE a.alias IS NOT NULL),'[]'::jsonb) aliases"
                + " FROM novels n LEFT JOIN novel_aliases a ON a.novel_id=n.id"
                + " GROUP BY n.id,n.data ORDER BY n.id");
    }
}
