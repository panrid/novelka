package panrid.space.novelka.server.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;
import panrid.space.novelka.server.repository.AuditRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Service
public final class AccountService {
    private final ReaderDatabase database;
    private final PasswordEncoder encoder;

    public AccountService(ReaderDatabase database, PasswordEncoder encoder,
            @Value("${novelka.owner.username:}") String username,
            @Value("${novelka.owner.password:}") String password) throws Exception {
        this.database = database;
        this.encoder = encoder;
        if (!username.isBlank() || !password.isBlank()) {
            String login = username(username);
            try (var jdbc = database.open()) {
                jdbc.transaction(() -> {
                    jdbc.exec("SELECT pg_advisory_xact_lock(728618)");
                    var accounts = new AccountRepository(jdbc);
                    if (accounts.hasOwner()) return null;
                    validatePassword(password);
                    if (accounts.find(login) != null) throw new IllegalStateException("Owner username already registered");
                    var owner = accounts.create(login, encoder.encode(password), Role.OWNER);
                    new AuditRepository(jdbc).add(owner.id(), "owner.bootstrap", owner.id(), Map.of());
                    return null;
                });
            }
        }
    }

    public Account register(Credentials credentials) throws Exception {
        String login = username(credentials.username());
        validatePassword(credentials.password());
        String hash = encoder.encode(credentials.password());
        try (var jdbc = database.open()) {
            return jdbc.transaction(() -> {
                jdbc.exec("SELECT pg_advisory_xact_lock(728618)");
                var accounts = new AccountRepository(jdbc);
                if (accounts.find(login) != null) throw new IllegalArgumentException("Цей логін уже зайнятий.");
                return accounts.create(login, hash, Role.READER);
            });
        }
    }

    public static String username(String value) {
        if (value == null) throw new IllegalArgumentException("Вкажіть логін.");
        String name = value.strip().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9_-]{2,39}"))
            throw new IllegalArgumentException("Логін: 3–40 латинських літер, цифр, _ або -.");
        return name;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Пароль: щонайменше 12 символів і не більше 72 байтів UTF-8.");
    }
}
