package space.panrid.novelka.webhook.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code X-Novelka-Signature}: {@code sha256=} and the hex HMAC-SHA256, keyed with the endpoint's
 * secret, of {@code <X-Novelka-Timestamp>.<body>}. The timestamp inside the signed text lets the
 * receiver refuse an old request replayed (say, older than 5 minutes).
 */
final class Signatures {

    static final String PREFIX = "sha256=";

    private Signatures() {
    }

    static String sign(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return PREFIX + HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
