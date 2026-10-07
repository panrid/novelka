package space.panrid.novelka.account;

/**
 * Google's side of «Увійти через Google» (OpenID Connect, code flow with PKCE): where to send
 * the person and who came back. Tests replace it with a fake.
 */
public interface GoogleSignIn {

    /** The Google account that signed in. {@code subject} never changes, the email may. */
    record Identity(String subject, String email, boolean emailVerified, String name) {
    }

    /** False when the site has no Google client: the button does not show. */
    boolean configured();

    String authorizationUrl(String state, String codeChallenge, String redirectUri);

    /** Trades the one-time code from the redirect for the person's identity. */
    Identity identity(String code, String codeVerifier, String redirectUri);
}
