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

    public Map<String, Object> adminView(String id) throws Exception {
        var rows = jdbc.rows("SELECT id,username,email,role,created_at FROM accounts WHERE id=?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public String email(String id) throws Exception {
        var rows = jdbc.rows("SELECT email FROM accounts WHERE id=?", id);
        return rows.isEmpty() ? null : (String) rows.getFirst().get("email");
    }

    public boolean emailTaken(String email, String exceptId) throws Exception {
        return !jdbc.rows("SELECT id FROM accounts WHERE email=? AND id<>?", email, exceptId).isEmpty();
    }

    /** A new address is unconfirmed until its owner opens the confirmation link. */
    public void updateEmail(String id, String email) throws Exception {
        jdbc.exec("UPDATE accounts SET email=?,email_verified_at=CASE WHEN email=? THEN email_verified_at END WHERE id=?", email, email, id);
    }

    public boolean emailVerified(String id) throws Exception {
        return !jdbc.rows("SELECT 1 FROM accounts WHERE id=? AND email_verified_at IS NOT NULL", id).isEmpty();
    }

    /** Confirms the address only if it is still the account's current one. */
    public boolean markVerified(String id, String email) throws Exception {
        return !jdbc.rows("UPDATE accounts SET email_verified_at=COALESCE(email_verified_at,now()) WHERE id=? AND email=? RETURNING id",
                id, email).isEmpty();
    }

    public void updatePassword(String id, String hash) throws Exception {
        jdbc.exec("UPDATE accounts SET password_hash=? WHERE id=?", hash, id);
    }

    public Map<String, Object> byEmail(String email) throws Exception {
        var rows = jdbc.rows("SELECT id,username,email FROM accounts WHERE email=?", email);
        return rows.isEmpty() ? null : rows.getFirst();
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

    /** Up to 10 nicknames starting with (then containing) the query, for pickers and mentions. */
    public List<Map<String, Object>> nicknames(String q) throws Exception {
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.rows("SELECT id,username FROM accounts WHERE username ILIKE ? ESCAPE '\\'"
                + " ORDER BY (username ILIKE ? ESCAPE '\\') DESC,lower(username),id LIMIT 10", "%" + escaped + "%", escaped + "%");
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

    /** Administrative list: search by nickname or email, filter by role; email is returned only here. */
    public ListPage<Map<String, Object>> list(ListQuery query, String role) throws Exception {
        if (!role.isEmpty() && !List.of("READER", "MODERATOR", "ADMIN", "OWNER").contains(role))
            throw new IllegalArgumentException("Невідома роль.");
        String where = " WHERE (?='' OR username ILIKE ? ESCAPE '\\' OR email ILIKE ? ESCAPE '\\') AND (?='' OR role=?)";
        Object[] filters = {query.q(), query.pattern(), query.pattern(), role, role};
        long total = ((Number) jdbc.rows("SELECT count(*) total FROM accounts" + where, filters).getFirst().get("total")).longValue();
        String order = query.order(Map.of("username", "username", "email", "email", "role", "role", "created", "created_at"), "created", "id");
        var items = jdbc.rows("SELECT id,username,email,role,created_at FROM accounts" + where + order + " LIMIT ? OFFSET ?",
                query.q(), query.pattern(), query.pattern(), role, role, query.size(), query.offset());
        return ListPage.of(items, query, total);
    }

    public void role(String id, Role role) throws Exception {
        jdbc.exec("UPDATE accounts SET role=? WHERE id=?", role.name(), id);
    }

    private static Account account(Map<String, Object> row) {
        return new Account((String) row.get("id"), (String) row.get("username"), Role.valueOf((String) row.get("role")));
    }
}
