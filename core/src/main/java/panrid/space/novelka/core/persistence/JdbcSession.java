package panrid.space.novelka.core.persistence;

import panrid.space.novelka.core.support.Json;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One connection and transaction boundary. Confined to the owning command/thread. */
public final class JdbcSession implements AutoCloseable {
    private final Connection c;

    public JdbcSession(String url, String user, String password) throws SQLException {
        c = DriverManager.getConnection(url, user, password);
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

    public <T> T one(String sql, Class<T> type, Object... args) throws Exception {
        var r = rows(sql, args);
        if (r.isEmpty()) throw new IllegalArgumentException("Record not found");
        return Json.decode(r.getFirst().get("data").toString(), type);
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

    public AutoCloseable lock(String novel) throws Exception {
        exec("SELECT pg_advisory_lock(hashtext(?))", novel);
        return () -> exec("SELECT pg_advisory_unlock(hashtext(?))", novel);
    }

    @Override
    public void close() throws SQLException {
        c.close();
    }
}
