package space.panrid.novelka.achievement.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every few minutes, counts again everyone who was on the site lately. */
@Component
@ConditionalOnProperty(name = "novelka.achievements.sweep", havingValue = "true", matchIfMissing = true)
class Sweeper {

    private static final Logger log = LoggerFactory.getLogger(Sweeper.class);

    private final Achievements achievements;

    Sweeper(Achievements achievements) {
        this.achievements = achievements;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    void tick() {
        try {
            achievements.sweep(achievements.recentlyActive());
        } catch (RuntimeException error) {
            log.error("Achievement sweep failed", error);
        }
    }
}
