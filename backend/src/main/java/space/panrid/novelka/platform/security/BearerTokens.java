package space.panrid.novelka.platform.security;

import java.util.Optional;

/**
 * Access tokens (етап 22.1), as the security chain sees them: a request with
 * {@code Authorization: Bearer} is signed in by its token instead of the session, and only where
 * the token's scopes reach. The account module's side implements it; without an implementation
 * bearer requests are refused.
 */
public interface BearerTokens {

    /** The grant of a presented token; empty when it is unknown, revoked, expired or its owner is gone. */
    Optional<TokenGrant> authenticate(String token, String address);

    /**
     * Why the token may not make this request, in Ukrainian; null when it may. Session-only
     * endpoints (the password, the tokens themselves) refuse every token.
     */
    String refusal(TokenGrant grant, String method, String path);

    /** After the request: a change made with a token goes into the journal. Must not throw. */
    void completed(TokenGrant grant, String method, String path, String address, int status);

    /**
     * The right ({@code read}, {@code studio}…) a token needs for this request; null when no token
     * may make it. The API's description (етап 22.4) names it on each operation.
     */
    default String scope(String method, String path) {
        return null;
    }
}
