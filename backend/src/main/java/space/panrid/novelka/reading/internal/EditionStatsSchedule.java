package space.panrid.novelka.reading.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The hourly recount; tests call {@link EditionStats#recount} themselves. */
@Component
@ConditionalOnProperty(name = "novelka.stats.recount", havingValue = "true", matchIfMissing = true)
class EditionStatsSchedule {

    private static final Logger log = LoggerFactory.getLogger(EditionStatsSchedule.class);

    private final EditionStats stats;

    EditionStatsSchedule(EditionStats stats) {
        this.stats = stats;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 3_600_000)
    void tick() {
        try {
            stats.recount();
        } catch (RuntimeException error) {
            log.error("Edition stats recount failed", error);
        }
    }
}
