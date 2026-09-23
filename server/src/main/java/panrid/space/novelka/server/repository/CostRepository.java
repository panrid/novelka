package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.Map;

/** The history is paged in SQL; aggregate and per-call views share the list contract. */
public final class CostRepository {
    private final JdbcSession jdbc;

    public CostRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public ListPage<Map<String, Object>> list(String novel, boolean details, ListQuery query, String stage) throws Exception {
        if (!stage.isEmpty() && !java.util.List.of("analyze", "translate", "proofread").contains(stage))
            throw new IllegalArgumentException("Невідомий етап витрат.");
        String base = " FROM ai_calls a JOIN jobs j ON j.id=a.job_id"
                + " WHERE (?::text IS NULL OR j.novel_id=?) AND (?='' OR a.stage=?)"
                + " AND (?='' OR j.novel_id ILIKE ? ESCAPE '\\' OR a.model ILIKE ? ESCAPE '\\')";
        String projection = details
                ? "SELECT a.id,a.job_id,j.novel_id,j.chapter,a.stage,a.segment,a.model,a.provider,a.prompt_version,"
                    + "a.glossary_revision,a.estimated_usd,a.actual_usd,a.cost_source,a.input_tokens,a.output_tokens,"
                    + "a.cached_tokens,a.state,a.request_id,a.duration_ms,a.created_at" + base
                : "SELECT j.novel_id,j.chapter,a.stage,a.model,count(*) calls,sum(a.estimated_usd) estimated_usd,"
                    + "sum(a.actual_usd) known_actual_usd,count(*) FILTER(WHERE a.actual_usd IS NULL) unknown_cost_calls,"
                    + "sum(a.input_tokens) input_tokens,sum(a.output_tokens) output_tokens" + base
                    + " GROUP BY j.novel_id,j.chapter,a.stage,a.model";
        Object[] filters = {novel, novel, stage, stage, query.q(), query.pattern(), query.pattern()};
        long total = ((Number) jdbc.rows("SELECT count(*) total FROM (" + projection + ") result", filters).getFirst().get("total")).longValue();
        var allowed = details
                ? Map.ofEntries(Map.entry("created", "created_at"), Map.entry("novel_id", "novel_id"),
                    Map.entry("chapter", "chapter"), Map.entry("stage", "stage"), Map.entry("model", "model"),
                    Map.entry("estimated_usd", "estimated_usd"), Map.entry("actual_usd", "actual_usd"),
                    Map.entry("input_tokens", "input_tokens"), Map.entry("output_tokens", "output_tokens"),
                    Map.entry("state", "state"))
                : Map.ofEntries(Map.entry("novel_id", "novel_id"), Map.entry("chapter", "chapter"),
                    Map.entry("stage", "stage"), Map.entry("model", "model"), Map.entry("estimated_usd", "estimated_usd"),
                    Map.entry("known_actual_usd", "known_actual_usd"), Map.entry("unknown_cost_calls", "unknown_cost_calls"),
                    Map.entry("calls", "calls"), Map.entry("input_tokens", "input_tokens"), Map.entry("output_tokens", "output_tokens"));
        String order = query.order(allowed, details ? "created" : "novel_id", details ? "id" : "chapter,stage,model");
        // Explicit null handling keeps unknown prices at the end regardless of direction.
        if (query.sort().equals("actual_usd") || query.sort().equals("known_actual_usd"))
            order = order.replace(" " + query.direction() + ",", " " + query.direction() + " NULLS LAST,");
        var rows = jdbc.rows("SELECT * FROM (" + projection + ") result" + order + " LIMIT ? OFFSET ?",
                novel, novel, stage, stage, query.q(), query.pattern(), query.pattern(), query.size(), query.offset());
        return ListPage.of(rows, query, total);
    }
}
