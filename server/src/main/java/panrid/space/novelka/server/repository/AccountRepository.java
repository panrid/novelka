package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccountRepository {
    private final JdbcSession jdbc;

    public AccountRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> credentials(String username) throws Exception {
        var rows = jdbc.rows("SELECT id,username,role,password_hash FROM accounts WHERE username=?", username);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Account find(String username) throws Exception {
        var row = credentials(username);
        return row == null ? null : account(row);
    }

    public Account byId(String id) throws Exception {
        var rows = jdbc.rows("SELECT id,username,role FROM accounts WHERE id=?", id);
        return rows.isEmpty() ? null : account(rows.getFirst());
    }

    public Account create(String username, String hash, Role role) throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.exec("INSERT INTO accounts(id,username,password_hash,role) VALUES(?,?,?,?)", id, username, hash, role.name());
        return new Account(id, username, role);
    }

    public boolean hasOwner() throws Exception {
        return !jdbc.rows("SELECT id FROM accounts WHERE role='OWNER'").isEmpty();
    }

    public List<Account> list(int offset) throws Exception {
        return jdbc.rows("SELECT id,username,role FROM accounts ORDER BY created_at,id LIMIT 50 OFFSET ?", offset)
                .stream().map(AccountRepository::account).toList();
    }

    public void role(String id, Role role) throws Exception {
        jdbc.exec("UPDATE accounts SET role=? WHERE id=?", role.name(), id);
    }

    private static Account account(Map<String, Object> row) {
        return new Account((String) row.get("id"), (String) row.get("username"), Role.valueOf((String) row.get("role")));
    }
}
