package panrid.space.novelka.server.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.models.ModelInfo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public final class ModelCatalogRepository {
    private final JdbcSession jdbc;

    public ModelCatalogRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public List<ModelInfo> models(String provider) throws Exception {
        var rows = jdbc.rows("SELECT models FROM model_catalogs WHERE provider=?", provider);
        return rows.isEmpty() ? List.of() : Json.M.readValue(rows.getFirst().get("models").toString(), new TypeReference<>() {});
    }

    public Instant refreshedAt(String provider) throws Exception {
        var rows = jdbc.rows("SELECT refreshed_at FROM model_catalogs WHERE provider=?", provider);
        if (rows.isEmpty()) return null;
        var value = rows.getFirst().get("refreshed_at");
        return value instanceof OffsetDateTime time ? time.toInstant() : ((java.sql.Timestamp) value).toInstant();
    }

    public void save(String provider, List<ModelInfo> models) throws Exception {
        jdbc.exec("INSERT INTO model_catalogs(provider,models,refreshed_at) VALUES(?,?::jsonb,now())"
                + " ON CONFLICT(provider) DO UPDATE SET models=excluded.models,refreshed_at=excluded.refreshed_at",
                provider, Json.write(models));
    }
}
