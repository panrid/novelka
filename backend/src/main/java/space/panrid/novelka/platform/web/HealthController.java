package space.panrid.novelka.platform.web;

import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness check for deployment: the application is up and can reach the database. */
@RestController
class HealthController {

    private final DSLContext db;

    HealthController(DSLContext db) {
        this.db = db;
    }

    @GetMapping("/api/health")
    Map<String, String> health() {
        db.selectOne().execute();
        return Map.of("status", "ok");
    }
}
