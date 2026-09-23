package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.Map;

public final class CatalogRepository {
    private static final String BASE = """
            WITH published AS (
                SELECT DISTINCT ON (j.novel_id,j.chapter) j.novel_id,j.chapter
                FROM jobs j JOIN chapters c ON c.novel_id=j.novel_id AND c.number=j.chapter
                WHERE j.state IN ('complete','needs-review') AND j.data->>'sourceHash'=c.source_hash
                ORDER BY j.novel_id,j.chapter,j.revision DESC
            ), ready AS (SELECT novel_id,count(*) ready_chapters FROM published GROUP BY novel_id),
            cards AS (
                SELECT n.id,n.data,COALESCE(r.ready_chapters,0) ready_chapters,
                    COALESCE((SELECT jsonb_agg(a.alias ORDER BY a.alias) FROM novel_aliases a WHERE a.novel_id=n.id),'[]'::jsonb) aliases,
                    COALESCE(NULLIF(n.data->>'titleUk',''),n.data->>'title') display_title,
                    COALESCE(NULLIF(n.data->>'authorUk',''),n.data->>'author') display_author
                FROM novels n LEFT JOIN ready r ON r.novel_id=n.id
            )
            """;
    private static final String FILTER = " WHERE (?='' OR id ILIKE ? ESCAPE '\\' OR display_title ILIKE ? ESCAPE '\\'"
            + " OR display_author ILIKE ? ESCAPE '\\' OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(aliases) AS alias(value) WHERE value ILIKE ? ESCAPE '\\'))"
            + " AND (NOT ? OR ready_chapters>0)";
    private final JdbcSession jdbc;

    public CatalogRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public ListPage<Map<String, Object>> list(ListQuery query, boolean readyOnly) throws Exception {
        Object[] filters = {query.q(), query.pattern(), query.pattern(), query.pattern(), query.pattern(), readyOnly};
        long total = ((Number) jdbc.rows(BASE + "SELECT count(*) total FROM cards" + FILTER, filters).getFirst().get("total")).longValue();
        String order = query.order(Map.of("title", "display_title", "author", "display_author", "ready", "ready_chapters", "id", "id"), "title", "id");
        var rows = jdbc.rows(BASE + "SELECT id,data,ready_chapters,aliases FROM cards" + FILTER + order + " LIMIT ? OFFSET ?",
                query.q(), query.pattern(), query.pattern(), query.pattern(), query.pattern(), readyOnly, query.size(), query.offset());
        return ListPage.of(rows, query, total);
    }
}
