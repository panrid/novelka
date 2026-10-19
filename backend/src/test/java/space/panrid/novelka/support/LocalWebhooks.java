package space.panrid.novelka.support;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import space.panrid.novelka.webhook.PublicAddresses;
import space.panrid.novelka.webhook.WebhookAddresses;

/**
 * The webhook guard of the tests: the real one ({@link PublicAddresses}), except that plain
 * {@code http://127.0.0.1:<port>} is let through for the ports a test opened its stub on.
 */
public class LocalWebhooks implements WebhookAddresses {

    private final PublicAddresses real = new PublicAddresses();
    private final Set<Integer> ports = ConcurrentHashMap.newKeySet();

    public void allow(int port) {
        ports.add(port);
    }

    @Override
    public Target resolve(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.strip());
            if ("http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && ports.contains(uri.getPort())) {
                return new Target(uri, List.of(InetAddress.getByAddress(new byte[] {127, 0, 0, 1})));
            }
        } catch (IllegalArgumentException | UnknownHostException notLocal) {
            // The real guard says what is wrong with it.
        }
        return real.resolve(url);
    }
}
