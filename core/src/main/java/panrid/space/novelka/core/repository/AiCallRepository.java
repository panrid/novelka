package panrid.space.novelka.core.repository;

import com.fasterxml.jackson.databind.JsonNode;

import panrid.space.novelka.core.model.AiCall;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.List;
import java.util.Map;

public final class AiCallRepository {
    private final JdbcSession jdbc;

    public AiCallRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public void start(AiCall a) throws Exception {
        jdbc.exec(
                "INSERT INTO"
                        + " ai_calls(id,job_id,stage,segment,model,prompt_version,glossary_revision,context,estimated_usd,state)"
                        + " VALUES(?,?,?,?,?,?,?,?::jsonb,?,?)",
                a.id(),
                a.jobId(),
                a.stage(),
                a.segment(),
                a.model(),
                a.promptVersion(),
                a.glossaryRevision(),
                a.contextJson(),
                a.estimateUsd(),
                a.state());
    }

    public double spent(String job) throws Exception {
        return ((Number)
                jdbc.rows(
                        "SELECT COALESCE(sum(COALESCE(actual_usd,estimated_usd)),0) total FROM ai_calls"
                                + " WHERE job_id=?",
                        job)
                        .getFirst()
                        .get("total"))
                .doubleValue();
    }

    public boolean hasUncertain(String job, String stage, int segment) throws Exception {
        return !jdbc.rows("SELECT id FROM ai_calls WHERE job_id=? AND stage=? AND segment=? AND state IN"
                + " ('pending','uncertain')", job, stage, segment).isEmpty();
    }

    public List<Map<String, Object>> previous(String job, String stage, int segment, String snapshot) throws Exception {
        return jdbc.rows("SELECT state,response FROM ai_calls WHERE job_id=? AND stage=? AND segment=? AND"
                + " context=?::jsonb ORDER BY created_at DESC LIMIT 1", job, stage, segment, snapshot);
    }

    public List<Map<String, Object>> contexts(String job) throws Exception {
        return jdbc.rows("""
                WITH RECURSIVE lineage(id) AS (
                    SELECT ?::text UNION
                    SELECT o.parent_job_id FROM work_origins o JOIN lineage l ON o.child_job_id=l.id
                ) SELECT context FROM ai_calls WHERE job_id IN (SELECT id FROM lineage)
                """, job);
    }

    public void authorizeRetry(String job) throws Exception {
        jdbc.exec("UPDATE ai_calls SET state='retry-authorized' WHERE job_id=? AND state IN ('pending','uncertain')", job);
    }

    public void uncertain(String id, long duration) throws Exception {
        jdbc.exec("UPDATE ai_calls SET state='uncertain',duration_ms=? WHERE id=?", duration, id);
    }

    public void finish(String id, String state, long duration, JsonNode body) throws Exception {
        var usage = body.path("usage");
        Object actual = usage.path("cost").isNumber() ? usage.path("cost").decimalValue() : null;
        Object input = usage.path("prompt_tokens").isNumber() ? usage.path("prompt_tokens").longValue() : null;
        Object output = usage.path("completion_tokens").isNumber() ? usage.path("completion_tokens").longValue() : null;
        Object cached = usage.path("prompt_tokens_details").path("cached_tokens").isNumber()
                ? usage.path("prompt_tokens_details").path("cached_tokens").longValue() : null;
        jdbc.exec("UPDATE ai_calls SET"
                + " state=?,provider=?,actual_usd=?,cost_source=?,input_tokens=?,output_tokens=?,cached_tokens=?,request_id=?,duration_ms=?,response=?::jsonb"
                + " WHERE id=?",
                state, body.path("provider").asText(null), actual, actual == null ? "unknown" : "api",
                input, output, cached, body.path("id").asText(null), duration, Json.write(body), id);
    }

    public List<Map<String, Object>> costs(String novel, boolean details) throws Exception {
        String where = novel == null ? "" : " WHERE j.novel_id=?";
        Object[] arguments = novel == null ? new Object[]{} : new Object[]{novel};
        return jdbc.rows(
                        details
                                ? "SELECT"
                                + " a.id,a.job_id,j.novel_id,j.chapter,a.stage,a.segment,a.model,a.provider,a.prompt_version,a.glossary_revision,a.estimated_usd,a.actual_usd,a.cost_source,a.input_tokens,a.output_tokens,a.cached_tokens,a.state,a.request_id,a.duration_ms,a.created_at"
                                + " FROM ai_calls a JOIN jobs j ON j.id=a.job_id"
                                + where
                                + " ORDER BY a.created_at"
                                : "SELECT j.novel_id,j.chapter,a.stage,a.model,count(*)"
                                + " calls,sum(a.estimated_usd) estimated_usd,sum(a.actual_usd)"
                                + " known_actual_usd,count(*) FILTER(WHERE a.actual_usd IS NULL)"
                                + " unknown_cost_calls,sum(a.input_tokens)"
                                + " input_tokens,sum(a.output_tokens) output_tokens FROM ai_calls a JOIN"
                                + " jobs j ON j.id=a.job_id"
                                + where
                                + " GROUP BY j.novel_id,j.chapter,a.stage,a.model ORDER BY"
                                + " j.novel_id,j.chapter,a.stage",
                        arguments);
    }
}
