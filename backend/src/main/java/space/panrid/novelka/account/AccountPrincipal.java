package space.panrid.novelka.account;

import java.io.Serializable;
import java.security.Principal;

/**
 * What the session remembers about the visitor: only the account id. Everything else
 * (nick, role) is read fresh, so changes apply to open sessions immediately.
 */
public record AccountPrincipal(long accountId) implements Principal, Serializable {

    @Override
    public String getName() {
        // Spring Session indexes sessions by this name; used to sign out everywhere.
        return Long.toString(accountId);
    }
}
