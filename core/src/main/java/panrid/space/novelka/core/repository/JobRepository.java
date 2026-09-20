package panrid.space.novelka.core.repository;

import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JobRepository {
    private final JdbcSession jdbc;

    public JobRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Work w) throws Exception {
        jdbc.exec(
                "INSERT INTO jobs(id,novel_id,chapter,revision,state,data) VALUES(?,?,?,?,?,?::jsonb) ON"
                        + " CONFLICT(id) DO UPDATE SET"
                        + " state=excluded.state,data=excluded.data,updated_at=now()",
                w.id(),
                w.novelId(),
                w.chapter(),
                w.revision(),
                w.state(),
                Json.write(w));
    }

    public Work job(String id) throws Exception {
        return jdbc.one("SELECT data FROM jobs WHERE id=?", Work.class, id);
    }

    public Work latest(String novel, int chapter) throws Exception {
        var r =
                jdbc.rows(
                        "SELECT data FROM jobs WHERE novel_id=? AND chapter=? ORDER BY revision DESC LIMIT 1",
                        novel,
                        chapter);
        return r.isEmpty() ? null : Json.decode(r.getFirst().get("data").toString(), Work.class);
    }

    public List<Work> completed(String novel) throws Exception {
        var result = new ArrayList<Work>();
        for (var r :
                jdbc.rows(
                        "SELECT latest.data FROM (SELECT DISTINCT ON(chapter) * FROM jobs WHERE novel_id=?"
                                + " ORDER BY chapter,revision DESC) latest JOIN chapters c ON"
                                + " c.novel_id=latest.novel_id AND c.number=latest.chapter WHERE"
                                + " latest.state='complete' AND latest.data->>'sourceHash'=c.source_hash ORDER BY"
                                + " latest.chapter",
                        novel))
            result.add(Json.decode(r.get("data").toString(), Work.class));
        return result;
    }

    public void recordMetrics(String job, int tokens, double usdPer5000Tokens) throws Exception {
        jdbc.exec("INSERT INTO job_metrics(job_id,source_tokens,tokenizer,target_usd) VALUES(?,?,?,?)",
                job, tokens, "o200k_base", tokens * usdPer5000Tokens / 5000);
    }

    public List<Map<String, Object>> status(String novel) throws Exception {
        return jdbc.rows("SELECT id,chapter,revision,state,updated_at FROM jobs WHERE novel_id=? ORDER BY chapter,revision", novel);
    }

    public List<Work> glossaryConsumers(String novel) throws Exception {
        var result = new ArrayList<Work>();
        for (var row : jdbc.rows("SELECT DISTINCT j.id,j.data FROM jobs j JOIN ai_calls a ON a.job_id=j.id WHERE"
                + " j.novel_id=? AND j.state IN ('complete','running','pending')", novel)) {
            result.add(Json.decode(row.get("data").toString(), Work.class));
        }
        return result;
    }
}
