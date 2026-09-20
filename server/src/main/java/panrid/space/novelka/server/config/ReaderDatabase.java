package panrid.space.novelka.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.persistence.MigrationRunner;

/** Migrates once at startup; each request owns and closes a separate connection. */
@Component
public final class ReaderDatabase {
    private final String url;
    private final String user;
    private final String password;

    public ReaderDatabase(
            @Value("${novelka.database.url}") String url,
            @Value("${novelka.database.user}") String user,
            @Value("${novelka.database.password}") String password) throws Exception {
        this.url = url;
        this.user = user;
        this.password = password;
        try (var jdbc = open()) {
            new MigrationRunner().migrate(jdbc);
        }
    }

    public JdbcSession open() throws java.sql.SQLException {
        return new JdbcSession(url, user, password);
    }
}
