package space.panrid.novelka.account.internal;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import space.panrid.novelka.account.GoogleSignIn;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google as an OpenID Connect provider. The ID token comes straight from Google's token
 * endpoint over HTTPS in exchange for the one-time code, so its signature need not be checked
 * (OpenID Connect Core 3.1.3.7); its issuer, audience and expiry still are.
 */
@Component
@EnableConfigurationProperties(GoogleOpenId.Properties.class)
class GoogleOpenId implements GoogleSignIn {

    @ConfigurationProperties("novelka.google")
    record Properties(String clientId, String clientSecret) {
    }

    private static final String AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String FAILED = "Google не відповів як слід. Спробуйте ще раз.";

    private final Properties properties;
    private final JsonMapper json;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    GoogleOpenId(Properties properties, JsonMapper json, Clock clock) {
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public boolean configured() {
        return present(properties.clientId()) && present(properties.clientSecret());
    }

    @Override
    public String authorizationUrl(String state, String codeChallenge, String redirectUri) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", properties.clientId());
        query.put("redirect_uri", redirectUri);
        query.put("response_type", "code");
        query.put("scope", "openid email profile");
        query.put("state", state);
        query.put("code_challenge", codeChallenge);
        query.put("code_challenge_method", "S256");
        query.put("prompt", "select_account");
        return AUTHORIZE + "?" + form(query);
    }

    @Override
    public Identity identity(String code, String codeVerifier, String redirectUri) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("redirect_uri", redirectUri);
        body.put("grant_type", "authorization_code");
        body.put("code_verifier", codeVerifier);
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(TOKEN))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(body))).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException error) {
            throw UserFacingException.badGateway(FAILED);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw UserFacingException.badGateway(FAILED);
        }
        if (response.statusCode() != 200) {
            throw UserFacingException.badGateway(FAILED);
        }
        return claims(json.readTree(response.body()).path("id_token").asString(""));
    }

    private Identity claims(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw UserFacingException.badGateway(FAILED);
        }
        JsonNode claims = json.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        boolean ours = ISSUERS.contains(claims.path("iss").asString(""))
                && properties.clientId().equals(claims.path("aud").asString(""))
                && claims.path("exp").asLong(0) > clock.instant().getEpochSecond();
        String subject = claims.path("sub").asString("");
        if (!ours || subject.isEmpty()) {
            throw UserFacingException.badGateway(FAILED);
        }
        // email_verified is a boolean, but has been seen as the string "true".
        JsonNode verified = claims.path("email_verified");
        return new Identity(subject, claims.path("email").asString(""),
                verified.isBoolean() ? verified.asBoolean() : "true".equals(verified.asString("")),
                claims.path("name").asString(""));
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
