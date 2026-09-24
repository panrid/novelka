package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Top-ups and the spending of charged tasks; see V18 for why the balance is derived instead of stored. */
public final class BalanceRepository {
    private static final String CHARGES = """
            SELECT COALESCE(sum(CASE WHEN t.state IN ('queued','running') THEN GREATEST((t.request->>'budgetUsd')::numeric,s.spent) END),0) AS reserved,
                COALESCE(sum(CASE WHEN t.state NOT IN ('queued','running') THEN s.spent END),0) AS spent
            FROM web_tasks t CROSS JOIN LATERAL (
                SELECT COALESCE(sum(GREATEST(0,COALESCE(j.final_usd,(SELECT sum(COALESCE(c.actual_usd,c.estimated_usd))
                    FROM ai_calls c WHERE c.job_id=j.job_id),0)-j.initial_usd))::numeric,0) AS spent
                FROM web_task_jobs j WHERE j.task_id=t.id) s
            WHERE t.actor_id=? AND t.charged
            """;
    private final JdbcSession jdbc;

    public BalanceRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    /** Serializes balance checks of one account, so two tasks cannot both spend the same money. */
    public void lock(String account) throws Exception {
        jdbc.rows("SELECT id FROM accounts WHERE id=? FOR UPDATE", account);
    }

    /** toppedUp, reserved (active tasks) and spent (finished tasks), in USD. */
    public Map<String, BigDecimal> totals(String account) throws Exception {
        var charges = jdbc.rows(CHARGES, account).getFirst();
        var topups = jdbc.rows("SELECT COALESCE(sum(amount_usd),0) AS total FROM balance_topups WHERE account_id=?", account).getFirst();
        return Map.of("toppedUp", decimal(topups.get("total")), "reserved", decimal(charges.get("reserved")), "spent", decimal(charges.get("spent")));
    }

    public void topUp(String account, BigDecimal amount, String actor, String note) throws Exception {
        jdbc.exec("INSERT INTO balance_topups(account_id,amount_usd,actor_id,note) VALUES(?,?,?,?)", account, amount, actor, note);
    }

    public List<Map<String, Object>> history(String account) throws Exception {
        return jdbc.rows("SELECT b.id,b.amount_usd,b.note,b.created_at,a.username AS actor FROM balance_topups b"
                + " JOIN accounts a ON a.id=b.actor_id WHERE b.account_id=? ORDER BY b.created_at DESC,b.id DESC LIMIT 50", account);
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal number ? number : new BigDecimal(String.valueOf(value));
    }
}
