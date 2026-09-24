package panrid.space.novelka.server.balance;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.BalanceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Everyone may run AI translation, paid from a personal balance that starts at zero. Only the site owner tops balances up;
 * the owner's own tasks use the site budget. Enqueuing reserves the task budget, so the balance never goes negative.
 */
@Service
public final class BalanceService {
    private static final BigDecimal LIMIT = new BigDecimal("1000");
    private final ReaderDatabase database;

    public BalanceService(ReaderDatabase database) { this.database = database; }

    public Balance balance(Account account) throws Exception {
        try (var jdbc = database.open()) {
            return balance(jdbc, account);
        }
    }

    public Balance balance(JdbcSession jdbc, Account account) throws Exception {
        var totals = new BalanceRepository(jdbc).totals(account.id());
        var available = totals.get("toppedUp").subtract(totals.get("reserved")).subtract(totals.get("spent"));
        return new Balance(available.setScale(4, RoundingMode.HALF_UP), totals.get("toppedUp"), totals.get("reserved"), totals.get("spent"),
                account.role() == Role.OWNER);
    }

    /** Whether the task is paid from the account's balance; checks funds under a row lock. Call inside the enqueue transaction. */
    public boolean charge(JdbcSession jdbc, Account account, double budgetUsd) throws Exception {
        if (account.role() == Role.OWNER) return false;
        new BalanceRepository(jdbc).lock(account.id());
        var available = balance(jdbc, account).available();
        if (available.compareTo(BigDecimal.valueOf(budgetUsd)) < 0)
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Недостатньо коштів: доступно $" + available.setScale(2, RoundingMode.DOWN)
                    + ", бюджет завдання $" + BigDecimal.valueOf(budgetUsd).setScale(2, RoundingMode.HALF_UP) + ". Баланс поповнює власник сайту.");
        return true;
    }

    public Map<String, Object> history(String accountId) throws Exception {
        try (var jdbc = database.open()) {
            var account = new AccountRepository(jdbc).byId(accountId);
            if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return Map.of("balance", balance(jdbc, account), "topups", new BalanceRepository(jdbc).history(accountId));
        }
    }

    public Balance topUp(Account owner, String accountId, TopUpRequest request) throws Exception {
        if (owner.role() != Role.OWNER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var amount = request.amountUsd();
        if (amount == null || amount.signum() == 0 || amount.abs().compareTo(LIMIT) > 0 || amount.scale() > 4)
            throw new IllegalArgumentException("Сума — від −1000 до 1000 доларів, не нуль, до 4 знаків після коми.");
        String note = request.note() == null ? "" : request.note().strip();
        if (note.length() > 500) throw new IllegalArgumentException("Коментар до поповнення — до 500 символів.");
        try (var jdbc = database.open()) {
            return jdbc.transaction(() -> {
                var account = new AccountRepository(jdbc).byId(accountId);
                if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                var balances = new BalanceRepository(jdbc);
                balances.lock(accountId);
                if (amount.signum() < 0 && balance(jdbc, account).available().add(amount).signum() < 0)
                    throw new IllegalArgumentException("Списання більше за доступний залишок.");
                balances.topUp(accountId, amount, owner.id(), note);
                new AuditRepository(jdbc).add(owner.id(), "balance.topup", accountId, Map.of("amountUsd", amount, "note", note));
                return balance(jdbc, account);
            });
        }
    }
}
