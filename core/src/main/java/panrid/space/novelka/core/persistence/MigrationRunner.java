package panrid.space.novelka.core.persistence;

/** Explicit migration registry; add a version here when adding its SQL resource. */
public final class MigrationRunner {
    private static final int LATEST_VERSION = 8;

    public void migrate(JdbcSession jdbc) throws Exception {
        jdbc.transaction(() -> {
            jdbc.exec("SELECT pg_advisory_xact_lock(728615)");
            jdbc.exec("CREATE TABLE IF NOT EXISTS schema_versions(version integer PRIMARY KEY)");
            for (int version = 1; version <= LATEST_VERSION; version++) {
                if (jdbc.rows("SELECT version FROM schema_versions WHERE version=?", version).isEmpty()) {
                    applyMigration(jdbc, version);
                    jdbc.exec("INSERT INTO schema_versions VALUES(?)", version);
                }
            }
            return null;
        });
    }

    private void applyMigration(JdbcSession jdbc, int version) throws Exception {
        var in = MigrationRunner.class.getResourceAsStream("/db/V" + version + ".sql");
        if (in == null) throw new IllegalStateException("Missing migration V" + version);
        try (in) {
            for (String sql :
                    new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) jdbc.exec(sql);
            }
        }
    }
}
