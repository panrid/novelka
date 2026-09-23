package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

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

    /** Login identifier is a normalized email or the current nickname; nickname history never matches. */
    public Map<String, Object> credentialsByEmail(String email) throws Exception {
        var rows = jdbc.rows("SELECT id,username,role,password_hash FROM accounts WHERE email=?", email);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public String passwordHash(String id) throws Exception {
        var rows = jdbc.rows("SELECT password_hash FROM accounts WHERE id=?", id);
        return rows.isEmpty() ? null : (String) rows.getFirst().get("password_hash");
    }

    public String email(String id) throws Exception {
        var rows = jdbc.rows("SELECT email FROM accounts WHERE id=?", id);
        return rows.isEmpty() ? null : (String) rows.getFirst().get("email");
    }

    public boolean emailTaken(String email, String exceptId) throws Exception {
        return !jdbc.rows("SELECT id FROM accounts WHERE email=? AND id<>?", email, exceptId).isEmpty();
    }

    public void updateEmail(String id, String email) throws Exception {
        jdbc.exec("UPDATE accounts SET email=? WHERE id=?", email, id);
    }

    public void rename(String id, String previous, String nickname) throws Exception {
        jdbc.exec("UPDATE accounts SET username=? WHERE id=?", nickname, id);
        jdbc.exec("INSERT INTO nickname_changes(account_id,previous_nickname,new_nickname) VALUES(?,?,?)", id, previous, nickname);
    }

    public Map<String, Object> nicknameStats(String id) throws Exception {
        return jdbc.rows("SELECT count(*) AS changes,max(changed_at) AS last_changed FROM nickname_changes WHERE account_id=?", id).getFirst();
    }

    public List<Map<String, Object>> nicknameHistory(String id) throws Exception {
        return jdbc.rows("SELECT previous_nickname,new_nickname,changed_at FROM nickname_changes WHERE account_id=?"
                + " ORDER BY changed_at DESC,id DESC", id);
    }

    public Account find(String username) throws Exception {
        var row = credentials(username);
        return row == null ? null : account(row);
    }

    public Account byId(String id) throws Exception {
        var rows = jdbc.rows("SELECT id,username,role FROM accounts WHERE id=?", id);
        return rows.isEmpty() ? null : account(rows.getFirst());
    }

    public Account create(String username, String email, String hash, Role role) throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.exec("INSERT INTO accounts(id,username,email,password_hash,role) VALUES(?,?,?,?,?)", id, username, email, hash, role.name());
        return new Account(id, username, role);
    }

    public boolean hasOwner() throws Exception {
        return !jdbc.rows("SELECT id FROM accounts WHERE role='OWNER'").isEmpty();
    }

    public ListPage<Account> list(ListQuery query, String role) throws Exception {
        if (!role.isEmpty() && !List.of("READER", "EDITOR", "ADMIN", "OWNER").contains(role))
            throw new IllegalArgumentException("Невідома роль.");
        String where = " WHERE (?='' OR username ILIKE ? ESCAPE '\\') AND (?='' OR role=?)";
        Object[] filters = {query.q(), query.pattern(), role, role};
        long total = ((Number) jdbc.rows("SELECT count(*) total FROM accounts" + where, filters).getFirst().get("total")).longValue();
        String order = query.order(Map.of("username", "username", "role", "role", "created", "created_at"), "created", "id");
        var items = jdbc.rows("SELECT id,username,role FROM accounts" + where + order + " LIMIT ? OFFSET ?",
                query.q(), query.pattern(), role, role, query.size(), query.offset()).stream().map(AccountRepository::account).toList();
        return ListPage.of(items, query, total);
    }

    public void role(String id, Role role) throws Exception {
        jdbc.exec("UPDATE accounts SET role=? WHERE id=?", role.name(), id);
    }

    private static Account account(Map<String, Object> row) {
        return new Account((String) row.get("id"), (String) row.get("username"), Role.valueOf((String) row.get("role")));
    }
}
