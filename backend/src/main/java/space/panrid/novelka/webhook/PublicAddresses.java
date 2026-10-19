package space.panrid.novelka.webhook;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Only public https addresses (SSRF): no userinfo, port 443 or from 1024, no names of local
 * networks, and every address the name resolves to must be public — not loopback, private,
 * link-local (cloud metadata at 169.254.169.254), carrier NAT, multicast, reserved or an IPv6
 * form that wraps one of those. The caller connects to exactly these addresses, so a DNS answer
 * changing after the check (rebinding) does not reach inside either.
 */
@Component
public class PublicAddresses implements WebhookAddresses {

    /** How a name becomes addresses; tests give their own answers. */
    @FunctionalInterface
    public interface Resolver {
        InetAddress[] all(String host) throws UnknownHostException;
    }

    static final int MAX_LENGTH = 500;
    private static final List<String> LOCAL_SUFFIXES = List.of(".localhost", ".local", ".internal", ".lan", ".home.arpa",
            ".localdomain", ".intranet", ".corp");

    private final Resolver resolver;

    public PublicAddresses() {
        this(InetAddress::getAllByName);
    }

    public PublicAddresses(Resolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Target resolve(String url) {
        URI uri = parse(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String lookup = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (lookup.equals("localhost") || LOCAL_SUFFIXES.stream().anyMatch(lookup::endsWith)
                || (!lookup.contains(".") && !lookup.contains(":"))) {
            throw inside();
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.all(lookup);
        } catch (UnknownHostException error) {
            throw UserFacingException.badRequest("Такого сайту не знайдено: " + host + ".");
        }
        if (addresses == null || addresses.length == 0) {
            throw UserFacingException.badRequest("Такого сайту не знайдено: " + host + ".");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw inside();
            }
        }
        return new Target(uri, List.of(addresses));
    }

    static URI parse(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw UserFacingException.badRequest("Вкажіть адресу до " + MAX_LENGTH + " символів.");
        }
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException error) {
            throw UserFacingException.badRequest("Це не схоже на адресу.");
        }
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            throw UserFacingException.badRequest("Адреса має починатися з https://.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw UserFacingException.badRequest("Це не схоже на адресу. Назву сайту кирилицею запишіть як xn--….");
        }
        if (uri.getRawUserInfo() != null) {
            throw UserFacingException.badRequest("Адреса без імені й пароля: перевіряйте підпис X-Novelka-Signature.");
        }
        if (uri.getRawFragment() != null) {
            throw UserFacingException.badRequest("Адреса без частини після «#».");
        }
        int port = uri.getPort();
        if (port != -1 && port != 443 && (port < 1024 || port > 65535)) {
            throw UserFacingException.badRequest("Порт — 443 або від 1024 до 65535.");
        }
        return uri;
    }

    /** Whether a request may go to this address: public unicast only. */
    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return publicV4(b[0] & 0xff, b[1] & 0xff, b[2] & 0xff);
        }
        if (address instanceof Inet6Address) {
            return publicV6(b);
        }
        return false;
    }

    private static boolean publicV4(int first, int second, int third) {
        return first != 0 // «this network»
                && first != 10
                && first != 127
                && !(first == 100 && second >= 64 && second <= 127) // carrier NAT
                && !(first == 169 && second == 254) // link-local, cloud metadata
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 168)
                && !(first == 192 && second == 0 && (third == 0 || third == 2)) // protocol assignments, documentation
                && !(first == 192 && second == 88 && third == 99) // 6to4 relays
                && !(first == 198 && (second == 18 || second == 19)) // benchmarking
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113)
                && first < 224; // multicast and reserved
    }

    private static boolean publicV6(byte[] b) {
        int first = ((b[0] & 0xff) << 8) | (b[1] & 0xff);
        int second = ((b[2] & 0xff) << 8) | (b[3] & 0xff);
        boolean zeroPrefix = true;
        for (int i = 0; i < 10; i++) {
            zeroPrefix &= b[i] == 0;
        }
        if (zeroPrefix) {
            return false; // ::, ::1, ::a.b.c.d and ::ffff:a.b.c.d — IPv4 wrapped, judged nowhere else
        }
        if ((first & 0xe000) != 0x2000) {
            return false; // only global unicast 2000::/3: not fc00::/7, fe80::/10, ff00::/8, 64:ff9b::/96…
        }
        return first != 0x2002 // 6to4 wraps an IPv4
                && !(first == 0x2001 && second == 0x0000) // Teredo
                && !(first == 0x2001 && second == 0x0db8) // documentation
                && !(first == 0x2001 && (second & 0xfff0) == 0x0010); // ORCHID
    }

    private static UserFacingException inside() {
        return UserFacingException.badRequest("Ця адреса веде у внутрішню мережу: вебхуки йдуть лише на публічні сайти.");
    }
}
