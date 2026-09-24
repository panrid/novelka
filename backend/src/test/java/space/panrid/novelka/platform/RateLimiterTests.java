package space.panrid.novelka.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.platform.web.RateLimiter;

class RateLimiterTests {

    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-09-24T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToTheLimitWithinTheWindowThenRecovers() {
        MovableClock clock = new MovableClock();
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        assertThat(limiter.tryAcquire("other")).as("keys are independent").isTrue();

        clock.now = clock.now.plus(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("ip")).isTrue();
    }
}
