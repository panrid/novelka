package space.panrid.novelka.platform.security;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.RateLimiter;

/**
 * How often the API may be asked (етап 22.2, 22.8): every request made with a token counts
 * against that token, and the public {@code /api/v1} without a token against the address it
 * comes from. Counted in memory, a sliding minute: one server is enough for the site, and a
 * restart simply forgets — a limit against floods, not an account of use.
 */
@Component
public class ApiRateLimits {

    /**
     * @param tokenRequestsPerMinute   requests one token may make in a minute, anywhere
     * @param addressRequestsPerMinute requests to {@code /api/v1} without a token from one address in a minute
     */
    @ConfigurationProperties("novelka.api")
    record Limits(int tokenRequestsPerMinute, int addressRequestsPerMinute) {

        Limits {
            tokenRequestsPerMinute = tokenRequestsPerMinute > 0 ? tokenRequestsPerMinute : 600;
            addressRequestsPerMinute = addressRequestsPerMinute > 0 ? addressRequestsPerMinute : 120;
        }
    }

    @Configuration
    @EnableConfigurationProperties(Limits.class)
    static class Registration {
    }

    private final RateLimiter byToken;
    private final RateLimiter byAddress;

    ApiRateLimits(Limits limits, Clock clock) {
        this.byToken = new RateLimiter(limits.tokenRequestsPerMinute(), Duration.ofMinutes(1), clock);
        this.byAddress = new RateLimiter(limits.addressRequestsPerMinute(), Duration.ofMinutes(1), clock);
    }

    /** Counts a request of the token; null when within the limit, otherwise how long to wait. */
    Duration token(long tokenId) {
        String key = Long.toString(tokenId);
        return byToken.tryAcquire(key) ? null : byToken.retryAfter(key);
    }

    /** Counts a request from the address; null when within the limit, otherwise how long to wait. */
    Duration address(String address) {
        String key = address == null ? "" : address;
        return byAddress.tryAcquire(key) ? null : byAddress.retryAfter(key);
    }

    /** Forgets every count, as a restart does (tests start each case afresh). */
    public void forget() {
        byToken.clear();
        byAddress.clear();
    }
}
