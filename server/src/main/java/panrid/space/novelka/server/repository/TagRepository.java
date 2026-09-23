package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.dto.TagView;
import panrid.space.novelka.server.tag.TagNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TagRepository {
    private final JdbcSession jdbc;

    public TagRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    /** Tags for suggestions and the catalog filter, most used first. */
    public List<Map<String, Object>> search(String q, int limit) throws Exception {
        String pattern = "%" + (q == null ? "" : q.strip().toLowerCase(java.util.Locale.forLanguageTag("uk")))
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.rows("""
                SELECT t.name,t.slug,count(nt.novel_id) AS novels FROM tags t LEFT JOIN novel_tags nt ON nt.tag_id=t.id
                WHERE t.slug LIKE ? ESCAPE '\\' GROUP BY t.id ORDER BY count(nt.novel_id) DESC,t.slug LIMIT ?
                """, pattern, limit);
    }

    public List<TagView> forNovel(String novel) throws Exception {
        return forNovels(List.of(novel)).getOrDefault(novel, List.of());
    }

    /** One query for a whole catalog page, so cards do not load tags one by one. */
    public Map<String, List<TagView>> forNovels(List<String> novels) throws Exception {
        var result = new HashMap<String, List<TagView>>();
        if (novels.isEmpty()) return result;
        for (var row : jdbc.rows("""
                SELECT nt.novel_id,t.name,t.slug FROM novel_tags nt JOIN tags t ON t.id=nt.tag_id
                WHERE nt.novel_id IN (SELECT jsonb_array_elements_text(?::jsonb)) ORDER BY t.slug
                """, Json.write(novels)))
            result.computeIfAbsent((String) row.get("novel_id"), key -> new ArrayList<>())
                    .add(new TagView((String) row.get("name"), (String) row.get("slug")));
        return result;
    }

    /** Replaces the novel's tag set; unknown tags are created with the first spelling. */
    public List<TagView> replace(String novel, List<String> names) throws Exception {
        var ids = new ArrayList<Long>();
        for (var name : names) {
            String slug = TagNames.slug(name);
            jdbc.exec("INSERT INTO tags(name,slug) VALUES(?,?) ON CONFLICT(slug) DO NOTHING", name, slug);
            ids.add(((Number) jdbc.rows("SELECT id FROM tags WHERE slug=?", slug).getFirst().get("id")).longValue());
        }
        jdbc.exec("DELETE FROM novel_tags WHERE novel_id=? AND tag_id NOT IN (SELECT (jsonb_array_elements_text(?::jsonb))::bigint)",
                novel, Json.write(ids));
        for (var id : ids) jdbc.exec("INSERT INTO novel_tags(novel_id,tag_id) VALUES(?,?) ON CONFLICT DO NOTHING", novel, id);
        return forNovel(novel);
    }
}
