package panrid.space.novelka.core.repository;

import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.List;
import java.util.Map;

public final class GlossaryRepository {
    private final JdbcSession jdbc;

    public GlossaryRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public Glossary glossary(String novel) throws Exception {
        var r = jdbc.rows("SELECT data FROM glossaries WHERE novel_id=?", novel);
        return r.isEmpty()
                ? new Glossary(0, List.of())
                : Json.decode(r.getFirst().get("data").toString(), Glossary.class);
    }

    /** Low-level write; canonical changes must go through GlossaryService.update. */
    public void save(String novel, Glossary glossary) throws Exception {
        jdbc.exec("INSERT INTO glossary_versions(novel_id,revision,data) VALUES(?,?,?::jsonb)",
                novel, glossary.revision(), Json.write(glossary));
        jdbc.exec("INSERT INTO glossaries VALUES(?,?,?::jsonb) ON CONFLICT(novel_id) DO UPDATE SET"
                + " revision=excluded.revision,data=excluded.data",
                novel, glossary.revision(), Json.write(glossary));
    }

    public void propose(String novel, String job, Object proposal) throws Exception {
        jdbc.exec("INSERT INTO glossary_proposals(novel_id,job_id,proposal) VALUES(?,?,?::jsonb)",
                novel, job, Json.write(proposal));
    }

    public List<Map<String, Object>> proposals(String novel) throws Exception {
        return jdbc.rows("SELECT id,job_id,proposal FROM glossary_proposals WHERE novel_id=? ORDER BY id", novel);
    }
}
