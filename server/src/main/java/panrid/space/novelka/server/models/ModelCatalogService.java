package panrid.space.novelka.server.models;

import org.springframework.stereotype.Service;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.ModelCatalogRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Refreshes provider model lists at most every 12 hours and keeps the last good list when the API fails. */
@Service
public final class ModelCatalogService {
    static final Duration TTL = Duration.ofHours(12);
    private final ReaderDatabase database;
    private final ModelDirectory directory;

    public ModelCatalogService(ReaderDatabase database, ModelDirectory directory) {
        this.database = database;
        this.directory = directory;
    }

    public synchronized ModelCatalog catalog(boolean force) throws Exception {
        String error = null;
        try (var jdbc = database.open()) {
            var repository = new ModelCatalogRepository(jdbc);
            var refreshed = repository.refreshedAt(directory.provider());
            if (force || refreshed == null || refreshed.isBefore(Instant.now().minus(TTL))) {
                try {
                    var models = directory.fetch();
                    if (models.isEmpty()) throw new IllegalStateException("Провайдер повернув порожній список моделей.");
                    repository.save(directory.provider(), models);
                } catch (Exception failure) {
                    error = failure.getMessage() == null ? "Список моделей тимчасово недоступний." : failure.getMessage();
                }
            }
            var at = repository.refreshedAt(directory.provider());
            var items = repository.models(directory.provider()).stream()
                    .sorted(Comparator.comparing(ModelInfo::suitable).reversed().thenComparing(ModelInfo::id)).toList();
            return new ModelCatalog(directory.provider(), at == null ? null : at.toString(),
                    at == null || at.isBefore(Instant.now().minus(TTL)), error, items);
        }
    }

    /** Stored data only: task creation never waits for the provider API. */
    public Optional<ModelInfo> stored(String model) throws Exception {
        try (var jdbc = database.open()) {
            List<ModelInfo> models = new ModelCatalogRepository(jdbc).models(directory.provider());
            return models.stream().filter(item -> item.id().equals(model)).findFirst();
        }
    }
}
