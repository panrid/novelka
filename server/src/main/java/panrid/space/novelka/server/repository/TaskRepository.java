package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.core.repository.NotificationEventRepository;
import panrid.space.novelka.server.task.TaskRequest;
import panrid.space.novelka.server.settings.SiteSettings;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TaskRepository {
    private final JdbcSession jdbc;

    public TaskRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public String enqueue(String actor, TaskRequest request, SiteSettings settings) throws Exception {
        var rows = jdbc.rows("INSERT INTO web_tasks(id,actor_id,request_key,operation,novel_id,request,settings)"
                + " VALUES(?,?,?,?,?,?::jsonb,?::jsonb) ON CONFLICT(actor_id,request_key) DO NOTHING RETURNING id",
                UUID.randomUUID().toString(), actor, request.requestKey(), request.operation(), request.novelId(),
                Json.write(request), Json.write(settings));
        if (!rows.isEmpty()) return rows.getFirst().get("id").toString();
        return jdbc.rows("SELECT id FROM web_tasks WHERE actor_id=? AND request_key=?", actor, request.requestKey())
                .getFirst().get("id").toString();
    }

    private static final String TASK_SELECT = """
                SELECT t.id,t.operation,t.novel_id,t.state,t.message,
                    COALESCE(t.current_job_id,t.request->>'jobId') AS current_job_id,t.cancel_requested,
                    current_job.chapter AS current_chapter,
                    latest_job.state AS latest_job_state,
                    COALESCE(current_job.id=latest_job.id AND current_job.state NOT IN ('complete','needs-review')
                        AND current_job.data->>'sourceHash'=c.source_hash,false) AS can_resume,
                    COALESCE(latest_job.data->>'sourceHash'=c.source_hash
                        AND jsonb_array_length(latest_job.data->'segments')>0
                        AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(latest_job.data->'segments') s
                            WHERE jsonb_array_length(s->'draft')=0),false) AS can_proofread,
                    t.request,t.created_at,t.updated_at,a.username,
                    COALESCE((SELECT sum(GREATEST(0,COALESCE(j.final_usd,(SELECT sum(COALESCE(c.actual_usd,c.estimated_usd))
                        FROM ai_calls c WHERE c.job_id=j.job_id),0)-j.initial_usd))
                        FROM web_task_jobs j WHERE j.task_id=t.id),0) spent_usd
                FROM web_tasks t JOIN accounts a ON a.id=t.actor_id
                    LEFT JOIN jobs current_job ON current_job.id=COALESCE(t.current_job_id,t.request->>'jobId')
                    LEFT JOIN chapters c ON c.novel_id=current_job.novel_id AND c.number=current_job.chapter
                    LEFT JOIN LATERAL (SELECT j.* FROM jobs j WHERE j.novel_id=current_job.novel_id
                        AND j.chapter=current_job.chapter ORDER BY revision DESC LIMIT 1) latest_job ON true
                """;

    public ListPage<Map<String, Object>> list(ListQuery query, String state, String operation, String novel) throws Exception {
        if (!state.isEmpty() && !List.of("queued", "running", "complete", "failed", "interrupted", "cancelled").contains(state))
            throw new IllegalArgumentException("Невідомий стан завдання.");
        if (!operation.isEmpty() && !List.of("import", "translate", "proofread", "resume").contains(operation))
            throw new IllegalArgumentException("Невідома операція.");
        String where = " WHERE (?='' OR t.state=?) AND (?='' OR t.operation=?) AND (?='' OR t.novel_id=?)"
                + " AND (?='' OR t.novel_id ILIKE ? ESCAPE '\\' OR t.id ILIKE ? ESCAPE '\\')";
        Object[] filters = {state, state, operation, operation, novel, novel, query.q(), query.pattern(), query.pattern()};
        long total = ((Number) jdbc.rows("SELECT count(*) total FROM web_tasks t" + where, filters).getFirst().get("total")).longValue();
        String order = query.order(Map.of("created", "t.created_at", "state", "t.state", "novel", "t.novel_id", "spent", "spent_usd"), "created", "t.id");
        var items = jdbc.rows(TASK_SELECT + where + order + " LIMIT ? OFFSET ?", state, state, operation, operation, novel, novel,
                query.q(), query.pattern(), query.pattern(), query.size(), query.offset());
        return ListPage.of(items, query, total);
    }

    public Map<String, Object> find(String id) throws Exception {
        var rows = jdbc.rows(TASK_SELECT + " WHERE t.id=?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean workerLock() throws Exception {
        return (Boolean) jdbc.rows("SELECT pg_try_advisory_lock(728620) locked").getFirst().get("locked");
    }

    public void recoverInterrupted() throws Exception {
        jdbc.transaction(() -> {
            jdbc.exec("UPDATE web_task_jobs j SET final_usd=COALESCE((SELECT sum(COALESCE(actual_usd,estimated_usd))"
                    + " FROM ai_calls WHERE job_id=j.job_id),0) WHERE final_usd IS NULL"
                    + " AND task_id IN (SELECT id FROM web_tasks WHERE state='running')");
            var interrupted = jdbc.rows("UPDATE web_tasks SET state='interrupted',message='Сервер перезапущено. Перевірте витрати й продовжіть job вручну.',"
                    + "updated_at=now() WHERE state='running' RETURNING id,novel_id");
            for (var task : interrupted) new NotificationEventRepository(jdbc).taskFinished(
                    (String) task.get("id"), (String) task.get("novel_id"), "interrupted");
            return null;
        });
    }

    public Map<String, Object> next() throws Exception {
        var rows = jdbc.rows("SELECT * FROM web_tasks WHERE state='queued' ORDER BY created_at LIMIT 1");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void state(String id, String state, String message) throws Exception {
        jdbc.transaction(() -> {
            var changed = jdbc.rows("UPDATE web_tasks SET state=?,message=?,updated_at=now() WHERE id=? RETURNING novel_id",
                    state, message, id);
            if (!changed.isEmpty()) new NotificationEventRepository(jdbc).taskFinished(id, (String) changed.getFirst().get("novel_id"), state);
            return null;
        });
    }

    public void job(String task, String job, double initial) throws Exception {
        jdbc.exec("INSERT INTO web_task_jobs(task_id,job_id,initial_usd) VALUES(?,?,?) ON CONFLICT DO NOTHING", task, job, initial);
        jdbc.exec("UPDATE web_tasks SET current_job_id=?,updated_at=now() WHERE id=?", job, task);
    }

    public void finishJob(String task, String job) throws Exception {
        jdbc.exec("UPDATE web_task_jobs j SET final_usd=COALESCE((SELECT sum(COALESCE(actual_usd,estimated_usd))"
                + " FROM ai_calls WHERE job_id=j.job_id),0) WHERE task_id=? AND job_id=?", task, job);
    }

    public void cancel(String id) throws Exception {
        jdbc.exec("UPDATE web_tasks SET cancel_requested=true, state=CASE WHEN state='queued' THEN 'cancelled' ELSE state END,"
                + "updated_at=now() WHERE id=? AND state IN ('queued','running')", id);
    }

    public boolean cancelled(String id) throws Exception {
        return (Boolean) jdbc.rows("SELECT cancel_requested FROM web_tasks WHERE id=?", id).getFirst().get("cancel_requested");
    }
}
