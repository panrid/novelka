package space.panrid.novelka.platform.security;

import java.util.Set;

import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;

/**
 * What a valid access token grants this request: whose it is, its scopes, the one translation it is
 * limited to ({@code editionId}, null — all the owner's; етап 22.12), and the authentication it
 * signs in with (the same principal a session of that person would hold).
 */
public record TokenGrant(long tokenId, long accountId, Set<String> scopes, Long editionId, Authentication authentication) {

    /** The request attribute that holds the grant of a request signed in by a token. */
    public static final String ATTRIBUTE = TokenGrant.class.getName();

    public TokenGrant {
        scopes = Set.copyOf(scopes);
    }

    /** The translation a request signed in by a limited token may work with; null when any. */
    public static Long edition(HttpServletRequest request) {
        TokenGrant grant = of(request);
        return grant == null ? null : grant.editionId();
    }

    /** The grant of this request, or null when it is not signed in by a token. */
    public static TokenGrant of(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof TokenGrant grant ? grant : null;
    }
}
