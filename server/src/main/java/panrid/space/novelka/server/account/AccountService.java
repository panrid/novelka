package panrid.space.novelka.server.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;
import panrid.space.novelka.server.repository.AuditRepository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public final class AccountService {
    private final ReaderDatabase database;
    private final PasswordEncoder encoder;

    public AccountService(ReaderDatabase database, PasswordEncoder encoder,
            @Value("${novelka.owner.username:}") String username,
            @Value("${novelka.owner.password:}") String password,
            @Value("${novelka.owner.email:}") String ownerEmail) throws Exception {
        this.database = database;
        this.encoder = encoder;
        String email = ownerEmail.isBlank() ? null : email(ownerEmail);
        if (!username.isBlank() || !password.isBlank()) {
            String login = username(username);
            try (var jdbc = database.open()) {
                jdbc.transaction(() -> {
                    jdbc.exec("SELECT pg_advisory_xact_lock(728618)");
                    var accounts = new AccountRepository(jdbc);
                    if (accounts.hasOwner()) {
                        // Existing owners created before emails existed receive the configured address once.
                        var owner = jdbc.rows("SELECT id,email FROM accounts WHERE role='OWNER'").getFirst();
                        if (email != null && owner.get("email") == null && !accounts.emailTaken(email, (String) owner.get("id")))
                            accounts.updateEmail((String) owner.get("id"), email);
                        return null;
                    }
                    validatePassword(password);
                    if (accounts.find(login) != null) throw new IllegalStateException("Owner username already registered");
                    var owner = accounts.create(login, email, encoder.encode(password), Role.OWNER);
                    new AuditRepository(jdbc).add(owner.id(), "owner.bootstrap", owner.id(), Map.of());
                    return null;
                });
            }
        }
    }

    public Account register(Credentials credentials) throws Exception {
        String login = username(credentials.username());
        String address = email(credentials.email());
        validatePassword(credentials.password());
        String hash = encoder.encode(credentials.password());
        try (var jdbc = database.open()) {
            return jdbc.transaction(() -> {
                jdbc.exec("SELECT pg_advisory_xact_lock(728618)");
                var accounts = new AccountRepository(jdbc);
                if (accounts.find(login) != null) throw new IllegalArgumentException("Цей нік уже зайнятий.");
                if (accounts.emailTaken(address, "")) throw new IllegalArgumentException("Цей email уже використовується.");
                return accounts.create(login, address, hash, Role.READER);
            });
        }
    }

    /** Profile with the nickname cooldown computed from append-only history. */
    public Map<String, Object> profile(Account account) throws Exception {
        try (var jdbc = database.open()) {
            var accounts = new AccountRepository(jdbc);
            var stats = accounts.nicknameStats(account.id());
            var result = new LinkedHashMap<String, Object>();
            result.put("id", account.id());
            result.put("username", account.username());
            result.put("email", accounts.email(account.id()));
            result.put("role", account.role());
            result.put("nicknameChanges", ((Number) stats.get("changes")).intValue());
            var available = nicknameAvailableAt(stats);
            result.put("nicknameAvailableAt", available == null || !available.isAfter(Instant.now()) ? null : available.toString());
            return result;
        }
    }

    public Account changeNickname(Account account, String value) throws Exception {
        String nickname = username(value);
        try (var jdbc = database.open()) {
            return jdbc.transaction(() -> {
                jdbc.exec("SELECT pg_advisory_xact_lock(728618)");
                var accounts = new AccountRepository(jdbc);
                var current = accounts.byId(account.id());
                if (current.username().equals(nickname)) throw new IllegalArgumentException("Це вже ваш нік.");
                var available = nicknameAvailableAt(accounts.nicknameStats(current.id()));
                if (available != null && available.isAfter(Instant.now()))
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Нік можна буде змінити після " + available + ".");
                if (accounts.find(nickname) != null) throw new IllegalArgumentException("Цей нік уже зайнятий.");
                accounts.rename(current.id(), current.username(), nickname);
                new AuditRepository(jdbc).add(current.id(), "account.nickname", current.id(),
                        Map.of("from", current.username(), "to", nickname));
                return accounts.byId(current.id());
            });
        }
    }

    public void changeEmail(Account account, String value, String password) throws Exception {
        String address = email(value);
        try (var jdbc = database.open()) {
            jdbc.transaction(() -> {
                jdbc.exec("SELECT pg_advisory_xact_lock(728618)");
                var accounts = new AccountRepository(jdbc);
                String hash = accounts.passwordHash(account.id());
                if (password == null || hash == null || !encoder.matches(password, hash))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Невірний поточний пароль.");
                if (accounts.emailTaken(address, account.id())) throw new IllegalArgumentException("Цей email уже використовується.");
                accounts.updateEmail(account.id(), address);
                new AuditRepository(jdbc).add(account.id(), "account.email", account.id(), Map.of());
                return null;
            });
        }
    }

    /** First change is immediate, then 2 hours, 2 weeks and 2 months after each previous change. */
    static Instant nicknameAvailableAt(Map<String, Object> stats) {
        int changes = ((Number) stats.get("changes")).intValue();
        if (changes == 0) return null;
        var last = stats.get("last_changed") instanceof OffsetDateTime time ? time.toInstant()
                : ((java.sql.Timestamp) stats.get("last_changed")).toInstant();
        return switch (changes) {
            case 1 -> last.plus(Duration.ofHours(2));
            case 2 -> last.plus(Duration.ofDays(14));
            default -> last.atOffset(ZoneOffset.UTC).plusMonths(2).toInstant();
        };
    }

    public static String username(String value) {
        if (value == null) throw new IllegalArgumentException("Вкажіть нік.");
        String name = value.strip().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9_-]{2,39}"))
            throw new IllegalArgumentException("Нік: 3–40 латинських літер, цифр, _ або -.");
        return name;
    }

    public static String email(String value) {
        if (value == null) throw new IllegalArgumentException("Вкажіть email.");
        String address = value.strip().toLowerCase(Locale.ROOT);
        if (address.length() > 254 || !address.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw new IllegalArgumentException("Вкажіть коректний email.");
        return address;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Пароль: щонайменше 12 символів і не більше 72 байтів UTF-8.");
    }
}
