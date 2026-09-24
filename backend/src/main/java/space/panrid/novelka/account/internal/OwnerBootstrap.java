package space.panrid.novelka.account.internal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;

/**
 * Creates the site owner on the first start from {@code NOVELKA_OWNER_*}. Does nothing once
 * an owner exists, and never promotes an existing reader: a taken nick or email stops startup.
 */
@Component
@EnableConfigurationProperties(OwnerBootstrap.OwnerProperties.class)
class OwnerBootstrap implements ApplicationRunner {

    @ConfigurationProperties("novelka.owner")
    record OwnerProperties(String nick, String email, String password) {

        boolean complete() {
            return nick != null && !nick.isBlank() && email != null && !email.isBlank()
                    && password != null && !password.isBlank();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final OwnerProperties owner;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Clock clock;

    OwnerBootstrap(OwnerProperties owner, AccountRepository accounts, PasswordEncoder passwords, Clock clock) {
        this.owner = owner;
        this.accounts = accounts;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.ownerExists() || !owner.complete()) {
            return;
        }
        String nick = AccountRules.nick(owner.nick());
        String email = AccountRules.email(owner.email());
        String password = AccountRules.password(owner.password());
        if (accounts.nickTaken(nick) || accounts.emailTaken(email)) {
            throw new IllegalStateException("NOVELKA_OWNER_NICK or NOVELKA_OWNER_EMAIL belongs to an existing account; "
                    + "an existing account is never promoted automatically");
        }
        accounts.insert(nick, email, passwords.encode(password), SiteRole.OWNER,
                OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
        log.info("Created the site owner {}. Remove NOVELKA_OWNER_* from the environment now.", nick);
    }
}
