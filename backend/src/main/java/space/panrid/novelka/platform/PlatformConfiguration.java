package space.panrid.novelka.platform;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SiteProperties.class)
class PlatformConfiguration {

    /** Injected wherever time matters, so tests can move it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
