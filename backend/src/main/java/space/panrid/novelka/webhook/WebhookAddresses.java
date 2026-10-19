package space.panrid.novelka.webhook;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * Where a webhook may be sent (SSRF): checked when the address is saved and again before every
 * attempt, since DNS may change meanwhile. The request then goes to exactly the addresses checked
 * here, never to a fresh DNS answer. {@link PublicAddresses} is the only one in production; tests
 * replace it to reach their local HTTP stub.
 */
public interface WebhookAddresses {

    /**
     * The address checked and resolved.
     *
     * @throws space.panrid.novelka.platform.web.UserFacingException (400) when it may not be used
     */
    Target resolve(String url);

    /** @param addresses where to connect, every one allowed, in the order to try */
    record Target(URI uri, List<InetAddress> addresses) {
    }
}
