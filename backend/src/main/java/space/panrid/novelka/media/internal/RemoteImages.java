package space.panrid.novelka.media.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Downloads a picture someone pasted a link to, so the site keeps its own copy. Guards
 * against SSRF: https only, every address of every host must be public, and the body is
 * capped at 5 MB. Redirects are followed by hand, a few at most, each checked the same way
 * (mitemin, where Syosetu's pictures live, always answers with one). A DNS answer changing
 * between the check and the request (rebinding) remains a theoretical gap.
 */
@Component
class RemoteImages {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final int MAX_REDIRECTS = 3;

    byte[] fetch(String rawUrl) {
        URI uri = parse(rawUrl);
        try {
            HttpResponse<InputStream> response = null;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                requirePublicHost(uri.getHost());
                response = http.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "Novelka image fetcher")
                        .header("Accept", "image/*")
                        .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                var location = response.headers().firstValue("Location");
                if (status < 300 || status >= 400 || location.isEmpty() || hop == MAX_REDIRECTS) {
                    break;
                }
                response.body().close();
                uri = parse(uri.resolve(location.get()).toString());
            }
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw UserFacingException.badRequest("За посиланням немає картинки (сайт відповів %d).".formatted(response.statusCode()));
                }
                return readCapped(body);
            }
        } catch (IOException error) {
            throw UserFacingException.badRequest("Не вдалося завантажити картинку за посиланням.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw UserFacingException.badRequest("Не вдалося завантажити картинку за посиланням.");
        }
    }

    private static URI parse(String raw) {
        try {
            URI uri = URI.create(raw == null ? "" : raw.strip());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw UserFacingException.badRequest("Посилання на картинку має починатися з https://.");
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw UserFacingException.badRequest("Це не схоже на посилання.");
        }
    }

    static void requirePublicHost(String host) {
        if (host.toLowerCase(Locale.ROOT).endsWith(".local") || host.equalsIgnoreCase("localhost")) {
            throw refused();
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw refused();
                }
            }
        } catch (UnknownHostException error) {
            throw UserFacingException.badRequest("Такого сайту не знайдено.");
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first != 0 && first != 127 && !(first == 100 && second >= 64 && second <= 127) // carrier NAT
                    && !(first == 192 && second == 0 && (b[2] & 0xff) == 0) && first < 224;
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) != 0xfc; // unique local fc00::/7
        }
        return false;
    }

    private static byte[] readCapped(InputStream body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = body.read(buffer)) != -1) {
            if (out.size() + read > MAX_BYTES) {
                throw UserFacingException.badRequest("Картинка за посиланням завелика: до 5 МБ.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static UserFacingException refused() {
        return UserFacingException.badRequest("З цієї адреси картинку взяти не можна.");
    }
}
