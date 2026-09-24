package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

import java.util.Map;

public final class EmailTokenRepository {
    private final JdbcSession jdbc;

    public EmailTokenRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public void create(String account, String purpose, String hash, String email, int minutes) throws Exception {
        jdbc.exec("INSERT INTO email_tokens(account_id,purpose,token_hash,email,expires_at) VALUES(?,?,?,?,now()+make_interval(mins=>?))",
                account, purpose, hash, email, minutes);
    }

    /** Letters of one purpose sent to the account recently: {@code last} seconds ago (null if none) and {@code today}. */
    public Map<String, Object> recent(String account, String purpose) throws Exception {
        return jdbc.rows("SELECT extract(epoch FROM now()-max(created_at)) AS last,"
                + " count(*) FILTER (WHERE created_at>now()-interval '1 day') AS today FROM email_tokens WHERE account_id=? AND purpose=?",
                account, purpose).getFirst();
    }

    /** Marks a valid token used and returns it, or null if it is unknown, expired or already used. */
    public Map<String, Object> consume(String hash, String purpose) throws Exception {
        var rows = jdbc.rows("UPDATE email_tokens SET used_at=now() WHERE token_hash=? AND purpose=? AND used_at IS NULL AND expires_at>now()"
                + " RETURNING account_id,email", hash, purpose);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Other open links of the same purpose stop working once one is used or the address changes. */
    public void revoke(String account, String purpose) throws Exception {
        jdbc.exec("UPDATE email_tokens SET used_at=now() WHERE account_id=? AND purpose=? AND used_at IS NULL", account, purpose);
    }
}
