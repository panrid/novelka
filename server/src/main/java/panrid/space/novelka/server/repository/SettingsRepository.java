package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.settings.SiteSettings;

public final class SettingsRepository {
    private final JdbcSession jdbc;

    public SettingsRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public SiteSettings read() throws Exception {
        var rows = jdbc.rows("SELECT data FROM site_settings WHERE id=1");
        return rows.isEmpty() ? null : Json.decode(rows.getFirst().get("data").toString(), SiteSettings.class);
    }

    public void save(SiteSettings settings) throws Exception {
        jdbc.exec("INSERT INTO site_settings VALUES(1,?::jsonb) ON CONFLICT(id) DO UPDATE SET data=excluded.data",
                Json.write(settings));
    }
}
