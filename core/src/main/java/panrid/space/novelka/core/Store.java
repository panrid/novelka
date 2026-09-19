package panrid.space.novelka.core;

import static panrid.space.novelka.core.Domain.*;

import java.sql.*;
import java.util.*;

public final class Store implements AutoCloseable {
  private final Connection c;

  public Store(String url, String user, String password) throws Exception {
    c = DriverManager.getConnection(url, user, password);
    migrate();
  }

  private void migrate() throws Exception {
    c.setAutoCommit(false);
    try {
      exec("SELECT pg_advisory_xact_lock(728615)");
      exec("CREATE TABLE IF NOT EXISTS schema_versions(version integer PRIMARY KEY)");
      if (rows("SELECT version FROM schema_versions WHERE version=1").isEmpty()) {
        var in = Store.class.getResourceAsStream("/db/V1.sql");
        if (in == null) throw new IllegalStateException("Missing migration");
        try (in) {
          for (String sql :
              new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split(";"))
            if (!sql.isBlank()) exec(sql);
        }
        exec("INSERT INTO schema_versions VALUES(1)");
      }
      c.commit();
    } catch (Exception e) {
      c.rollback();
      throw e;
    } finally {
      c.setAutoCommit(true);
    }
  }

  public void exec(String sql, Object... args) throws SQLException {
    try (var s = c.prepareStatement(sql)) {
      bind(s, args);
      s.execute();
    }
  }

  public List<Map<String, Object>> rows(String sql, Object... args) throws SQLException {
    try (var s = c.prepareStatement(sql)) {
      bind(s, args);
      try (var r = s.executeQuery()) {
        var list = new ArrayList<Map<String, Object>>();
        while (r.next()) {
          var row = new LinkedHashMap<String, Object>();
          for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
            row.put(
                r.getMetaData().getColumnLabel(i),
                r.getMetaData().getColumnTypeName(i).equals("jsonb")
                    ? (r.getString(i) == null ? null : Json.read(r.getString(i)))
                    : r.getObject(i));
          list.add(row);
        }
        return list;
      }
    }
  }

  private static void bind(PreparedStatement s, Object[] args) throws SQLException {
    for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
  }

  private <T> T one(String sql, Class<T> type, Object... args) throws Exception {
    var r = rows(sql, args);
    if (r.isEmpty()) throw new IllegalArgumentException("Record not found");
    return Json.decode(r.getFirst().get("data").toString(), type);
  }

  public void save(Novel n) throws Exception {
    exec(
        "INSERT INTO novels VALUES(?,?::jsonb) ON CONFLICT(id) DO UPDATE SET data=excluded.data",
        n.id(),
        Json.write(n));
  }

  public Novel novel(String id) throws Exception {
    return one("SELECT data FROM novels WHERE id=?", Novel.class, id);
  }

  public void save(String novel, Chapter ch) throws Exception {
    String h = hash(ch.blocks());
    exec(
        "INSERT INTO chapter_versions(novel_id,number,source_hash,data) VALUES(?,?,?,?::jsonb) ON"
            + " CONFLICT DO NOTHING",
        novel,
        ch.number(),
        h,
        Json.write(ch));
    exec(
        "INSERT INTO chapters VALUES(?,?,?,?::jsonb) ON CONFLICT(novel_id,number) DO UPDATE SET"
            + " source_hash=excluded.source_hash,data=excluded.data",
        novel,
        ch.number(),
        h,
        Json.write(ch));
  }

  public Chapter chapter(String novel, int number) throws Exception {
    return one(
        "SELECT data FROM chapters WHERE novel_id=? AND number=?", Chapter.class, novel, number);
  }

  public Glossary glossary(String novel) throws Exception {
    var r = rows("SELECT data FROM glossaries WHERE novel_id=?", novel);
    return r.isEmpty()
        ? new Glossary(0, List.of())
        : Json.decode(r.getFirst().get("data").toString(), Glossary.class);
  }

  public <T> T transaction(java.util.concurrent.Callable<T> action) throws Exception {
    boolean outer = c.getAutoCommit();
    if (outer) c.setAutoCommit(false);
    try {
      T result = action.call();
      if (outer) c.commit();
      return result;
    } catch (Exception e) {
      if (outer) c.rollback();
      throw e;
    } finally {
      if (outer) c.setAutoCommit(true);
    }
  }

  public void glossary(String novel, Glossary g) throws Exception {
    transaction(
        () -> {
          Glossary old = glossary(novel);
          exec(
              "INSERT INTO glossary_versions(novel_id,revision,data) VALUES(?,?,?::jsonb)",
              novel,
              g.revision(),
              Json.write(g));
          exec(
              "INSERT INTO glossaries VALUES(?,?,?::jsonb) ON CONFLICT(novel_id) DO UPDATE SET"
                  + " revision=excluded.revision,data=excluded.data",
              novel,
              g.revision(),
              Json.write(g));
          var changed =
              old.entries().stream()
                  .filter(e -> g.entries().stream().noneMatch(e::equals))
                  .map(Entry::key)
                  .toList();
          if (!changed.isEmpty()) {
            // Use saved request snapshots, including tool responses, to identify consumers.
            var affected =
                rows(
                    "SELECT DISTINCT j.id,j.data FROM jobs j JOIN ai_calls a ON a.job_id=j.id WHERE"
                        + " j.novel_id=? AND j.state IN ('complete','running','pending')",
                    novel);
            for (var row : affected) {
              String id = row.get("id").toString();
              boolean depends = false;
              for (var call : rows("SELECT context FROM ai_calls WHERE job_id=?", id)) {
                var context = Json.read(call.get("context").toString());
                for (String key : changed)
                  if (containsEntry(context, key)) {
                    depends = true;
                    break;
                  }
              }
              if (depends) {
                Work w = Json.decode(row.get("data").toString(), Work.class);
                save(
                    new Work(
                        w.id(),
                        w.novelId(),
                        w.chapter(),
                        w.sourceHash(),
                        w.revision(),
                        w.segments(),
                        "needs-review",
                        w.summary()));
              }
            }
          }
          return null;
        });
  }

  private static boolean containsEntry(com.fasterxml.jackson.databind.JsonNode node, String key) {
    if (node.isObject() && node.path("key").asText().equals(key)) return true;
    if (node.isContainerNode()) {
      for (var child : node) if (containsEntry(child, key)) return true;
    }
    if (node.isTextual()) {
      String text = node.asText();
      if (text.startsWith("{") || text.startsWith("[")) {
        try {
          return containsEntry(Json.read(text), key);
        } catch (Exception ignored) {
        }
      }
    }
    return false;
  }

  public AutoCloseable lock(String novel) throws Exception {
    exec("SELECT pg_advisory_lock(hashtext(?))", novel);
    return () -> exec("SELECT pg_advisory_unlock(hashtext(?))", novel);
  }

  public void save(Work w) throws Exception {
    exec(
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
    return one("SELECT data FROM jobs WHERE id=?", Work.class, id);
  }

  public Work latest(String novel, int chapter) throws Exception {
    var r =
        rows(
            "SELECT data FROM jobs WHERE novel_id=? AND chapter=? ORDER BY revision DESC LIMIT 1",
            novel,
            chapter);
    return r.isEmpty() ? null : Json.decode(r.getFirst().get("data").toString(), Work.class);
  }

  public List<Work> completed(String novel) throws Exception {
    var result = new ArrayList<Work>();
    for (var r :
        rows(
            "SELECT latest.data FROM (SELECT DISTINCT ON(chapter) * FROM jobs WHERE novel_id=?"
                + " ORDER BY chapter,revision DESC) latest JOIN chapters c ON"
                + " c.novel_id=latest.novel_id AND c.number=latest.chapter WHERE"
                + " latest.state='complete' AND latest.data->>'sourceHash'=c.source_hash ORDER BY"
                + " latest.chapter",
            novel)) result.add(Json.decode(r.get("data").toString(), Work.class));
    return result;
  }

  public void start(Call a) throws Exception {
    exec(
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
            rows(
                    "SELECT COALESCE(sum(COALESCE(actual_usd,estimated_usd)),0) total FROM ai_calls"
                        + " WHERE job_id=?",
                    job)
                .getFirst()
                .get("total"))
        .doubleValue();
  }

  @Override
  public void close() throws SQLException {
    c.close();
  }
}
