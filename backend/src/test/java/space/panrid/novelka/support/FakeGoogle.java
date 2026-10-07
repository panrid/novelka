package space.panrid.novelka.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import space.panrid.novelka.account.GoogleSignIn;
import space.panrid.novelka.platform.web.UserFacingException;

/** Google in tests: a test hands out a one-time code for the person who «signed in at Google». */
public class FakeGoogle implements GoogleSignIn {

    private final Map<String, Identity> codes = new ConcurrentHashMap<>();

    public String code(Identity identity) {
        String code = UUID.randomUUID().toString();
        codes.put(code, identity);
        return code;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public String authorizationUrl(String state, String codeChallenge, String redirectUri) {
        return "https://accounts.google.test/auth?state=" + state + "&redirect_uri=" + redirectUri;
    }

    @Override
    public Identity identity(String code, String codeVerifier, String redirectUri) {
        Identity identity = codes.remove(code);
        if (identity == null) {
            throw UserFacingException.badGateway("Google не відповів як слід. Спробуйте ще раз.");
        }
        return identity;
    }
}
