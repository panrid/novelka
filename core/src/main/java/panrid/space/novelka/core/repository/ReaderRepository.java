package panrid.space.novelka.core.repository;

import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.List;
import java.util.Map;

/** Read projections. Keep the last publishable revision visible while a replacement is prepared. */
public final class ReaderRepository {
    private static final String PUBLISHED = """
            WITH published AS (
                SELECT DISTINCT ON (j.novel_id, j.chapter) j.*
                FROM jobs j JOIN chapters c ON c.novel_id=j.novel_id AND c.number=j.chapter
                WHERE j.state IN ('complete', 'needs-review') AND j.data->>'sourceHash'=c.source_hash
                ORDER BY j.novel_id, j.chapter, j.revision DESC
            )
            """;
    private final JdbcSession jdbc;

    public ReaderRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> chapterStats(String novel) throws Exception {
        return jdbc.rows(PUBLISHED + "SELECT count(*) ready,min(chapter) first_chapter FROM published WHERE novel_id=?", novel).getFirst();
    }

    public boolean hasChapter(String novel, int chapter) throws Exception {
        return !jdbc.rows(PUBLISHED + "SELECT 1 FROM published WHERE novel_id=? AND chapter=?", novel, chapter).isEmpty();
    }

    public long chapterCount(String novel, String pattern) throws Exception {
        return ((Number) jdbc.rows(PUBLISHED + """
                SELECT count(*) total FROM (
                    SELECT p.chapter,COALESCE(jsonb_path_query_first(p.data,
                        '$.segments[*].revised[*] ? (@.kind == "heading").text') #>> '{}',c.data->>'title') title
                    FROM published p JOIN chapters c ON c.novel_id=p.novel_id AND c.number=p.chapter WHERE p.novel_id=?
                ) list WHERE (?='' OR title ILIKE ? ESCAPE '\\' OR chapter::text LIKE ? ESCAPE '\\')
                """, novel, pattern.equals("%%") ? "" : pattern, pattern, pattern).getFirst().get("total")).longValue();
    }

    public List<Map<String, Object>> chapterPage(String novel, String pattern, String direction, int limit, int offset) throws Exception {
        return jdbc.rows(PUBLISHED + """
                SELECT chapter,title,revision FROM (
                    SELECT p.chapter,p.revision,COALESCE(jsonb_path_query_first(p.data,
                        '$.segments[*].revised[*] ? (@.kind == "heading").text') #>> '{}',c.data->>'title') title
                    FROM published p JOIN chapters c ON c.novel_id=p.novel_id AND c.number=p.chapter WHERE p.novel_id=?
                ) list WHERE (?='' OR title ILIKE ? ESCAPE '\\' OR chapter::text LIKE ? ESCAPE '\\')
                ORDER BY chapter
                """ + direction + " LIMIT ? OFFSET ?", novel, pattern.equals("%%") ? "" : pattern, pattern, pattern, limit, offset);
    }

    public Map<String, Object> neighbours(String novel, int chapter) throws Exception {
        return jdbc.rows(PUBLISHED + "SELECT max(chapter) FILTER(WHERE chapter<?) previous,"
                + " min(chapter) FILTER(WHERE chapter>?) next FROM published WHERE novel_id=?", chapter, chapter, novel).getFirst();
    }

    public Work chapter(String novel, int chapter) throws Exception {
        var rows = jdbc.rows(PUBLISHED + "SELECT data FROM published WHERE novel_id=? AND chapter=?", novel, chapter);
        return rows.isEmpty() ? null : Json.decode(rows.getFirst().get("data").toString(), Work.class);
    }
}
