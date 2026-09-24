package space.panrid.novelka.account.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Anti-abuse limits for anonymous actions.
 *
 * @param signInsPerAddress       sign-in attempts from one address per 10 minutes
 * @param signInsPerLogin         sign-in attempts for one nick or email per 10 minutes
 * @param registrationsPerAddress new accounts from one address per hour
 */
@ConfigurationProperties("novelka.limits")
record AccountLimits(int signInsPerAddress, int signInsPerLogin, int registrationsPerAddress) {

    AccountLimits {
        signInsPerAddress = signInsPerAddress > 0 ? signInsPerAddress : 30;
        signInsPerLogin = signInsPerLogin > 0 ? signInsPerLogin : 10;
        registrationsPerAddress = registrationsPerAddress > 0 ? registrationsPerAddress : 5;
    }

    @Configuration
    @EnableConfigurationProperties(AccountLimits.class)
    static class Registration {
    }
}
