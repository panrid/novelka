package panrid.space.novelka.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.config.ReaderDatabase;

import java.util.Map;

/** Public readiness probe for deploys: the application answers and PostgreSQL accepts a query. */
@RestController
public final class HealthController {
    private final ReaderDatabase database;

    public HealthController(ReaderDatabase database) { this.database = database; }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try (var jdbc = database.open()) {
            jdbc.rows("SELECT 1");
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "unavailable"));
        }
    }
}
