package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.List;
import java.util.Map;

/** Reads slices of the current JSON glossary without sending the whole document to the browser. */
public final class GlossaryEntryRepository {
    private final JdbcSession jdbc;

    public GlossaryEntryRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public ListPage<Entry> list(String novel, ListQuery query, String kind) throws Exception {
        if (!kind.isEmpty() && !List.of("character", "term", "place", "other").contains(kind))
            throw new IllegalArgumentException("Невідомий тип запису словника.");
        String from = " FROM glossaries g CROSS JOIN LATERAL jsonb_array_elements(g.data->'entries') AS e(value)";
        String where = " WHERE g.novel_id=? AND (?='' OR e.value->>'kind'=?)"
                + " AND (?='' OR e.value->>'japanese' ILIKE ? ESCAPE '\\' OR e.value->>'ukrainian' ILIKE ? ESCAPE '\\'"
                + " OR e.value->>'reading' ILIKE ? ESCAPE '\\' OR e.value->>'aliases' ILIKE ? ESCAPE '\\')";
        long total = ((Number) jdbc.rows("SELECT count(*) total" + from + where, novel, kind, kind, query.q(),
                query.pattern(), query.pattern(), query.pattern(), query.pattern()).getFirst().get("total")).longValue();
        String order = query.order(Map.of("japanese", "e.value->>'japanese'", "ukrainian", "e.value->>'ukrainian'",
                "kind", "e.value->>'kind'", "chapter", "(e.value->>'sourceChapter')::integer"), "japanese", "e.value->>'key'");
        var items = jdbc.rows("SELECT e.value entry" + from + where + order + " LIMIT ? OFFSET ?", novel, kind, kind,
                query.q(), query.pattern(), query.pattern(), query.pattern(), query.pattern(), query.size(), query.offset())
                .stream().map(row -> Json.decode(row.get("entry").toString(), Entry.class)).toList();
        return ListPage.of(items, query, total);
    }
}
