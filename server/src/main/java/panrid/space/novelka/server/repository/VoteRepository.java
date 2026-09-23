package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.vote.VoteSummary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoteRepository {
    private final JdbcSession jdbc;

    public VoteRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public void set(String type, String target, String account, int value) throws Exception {
        if (value == 0) jdbc.exec("DELETE FROM votes WHERE target_type=? AND target_id=? AND account_id=?", type, target, account);
        else jdbc.exec("""
                INSERT INTO votes(target_type,target_id,account_id,value) VALUES(?,?,?,?)
                ON CONFLICT(target_type,target_id,account_id) DO UPDATE SET value=excluded.value,updated_at=now()
                """, type, target, account, (short) value);
    }

    public VoteSummary summary(String type, String target, String account) throws Exception {
        return summaries(type, List.of(target), account).get(target);
    }

    /** Scores and the viewer's votes for a page of targets in one query. */
    public Map<String, VoteSummary> summaries(String type, List<String> targets, String account) throws Exception {
        var result = new HashMap<String, VoteSummary>();
        for (var target : targets) result.put(target, new VoteSummary(0, 0));
        if (targets.isEmpty()) return result;
        for (var row : jdbc.rows("""
                SELECT target_id,sum(value) AS score,COALESCE(max(value) FILTER (WHERE account_id=?),0) AS mine FROM votes
                WHERE target_type=? AND target_id IN (SELECT jsonb_array_elements_text(?::jsonb)) GROUP BY target_id
                """, account, type, Json.write(targets)))
            result.put((String) row.get("target_id"), new VoteSummary(((Number) row.get("score")).longValue(), ((Number) row.get("mine")).intValue()));
        return result;
    }
}
