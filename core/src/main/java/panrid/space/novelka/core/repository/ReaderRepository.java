package panrid.space.novelka.core.repository;

import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.List;
import java.util.Map;

/** Read projections. The latest revision must be complete and match the current source. */
public final class ReaderRepository {
    private static final String PUBLISHED = """
            WITH latest AS (
                SELECT DISTINCT ON (novel_id, chapter) novel_id, chapter, revision, state, data
                FROM jobs ORDER BY novel_id, chapter, revision DESC
            ), published AS (
                SELECT j.* FROM latest j JOIN chapters c
                    ON c.novel_id=j.novel_id AND c.number=j.chapter
                WHERE j.state='complete' AND j.data->>'sourceHash'=c.source_hash
            )
            """;
    private final JdbcSession jdbc;

    public ReaderRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> catalog() throws Exception {
        return jdbc.rows(PUBLISHED + """
                SELECT n.data, (SELECT count(*) FROM published p WHERE p.novel_id=n.id) ready_chapters,
                    COALESCE((SELECT jsonb_agg(a.alias ORDER BY a.alias)
                        FROM novel_aliases a WHERE a.novel_id=n.id), '[]'::jsonb) aliases
                FROM novels n ORDER BY n.id
                """);
    }

    public List<Map<String, Object>> chapters(String novel) throws Exception {
        return jdbc.rows(PUBLISHED + """
                SELECT p.chapter, p.revision,
                    COALESCE(
                        jsonb_path_query_first(p.data, '$.segments[*].revised[*] ? (@.kind == "heading").text') #>> '{}',
                        c.data->>'title') title
                FROM published p JOIN chapters c ON c.novel_id=p.novel_id AND c.number=p.chapter
                WHERE p.novel_id=? ORDER BY p.chapter
                """, novel);
    }

    public Work chapter(String novel, int chapter) throws Exception {
        var rows = jdbc.rows(PUBLISHED + "SELECT data FROM published WHERE novel_id=? AND chapter=?", novel, chapter);
        return rows.isEmpty() ? null : Json.decode(rows.getFirst().get("data").toString(), Work.class);
    }
}
