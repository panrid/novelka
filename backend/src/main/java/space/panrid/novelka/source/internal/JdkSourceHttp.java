package space.panrid.novelka.source.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceHttp;

/**
 * Plain requests to the source sites, one at a time per site with a second's pause in
 * between so no site is hammered. Syosetu's 18+ site needs the «over18» cookie.
 */
@Component
class JdkSourceHttp implements SourceHttp {

    private static final long PAUSE_MILLIS = 1_000;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    /** Per site: the lock is held for the request and the pause before it. */
    private final Map<String, long[]> lastRequest = new ConcurrentHashMap<>();

    @Override
    public String get(String url) {
        URI uri = URI.create(url);
        String site = site(uri.getHost());
        long[] last = lastRequest.computeIfAbsent(site, key -> new long[1]);
        synchronized (last) {
            try {
                long wait = last[0] + PAUSE_MILLIS - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(wait);
                }
                HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "Mozilla/5.0 (compatible; Novelka)");
                if (site.equals("syosetu.com")) {
                    request.header("Cookie", "over18=yes");
                }
                HttpResponse<String> response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
                last[0] = System.currentTimeMillis();
                if (response.statusCode() == 404) {
                    throw UserFacingException.notFound("На %s такої сторінки немає.".formatted(site));
                }
                if (response.statusCode() != 200) {
                    throw UserFacingException.badGateway("%s не відповідає як слід (код %d). Спробуйте пізніше."
                            .formatted(site, response.statusCode()));
                }
                return response.body();
            } catch (IOException error) {
                last[0] = System.currentTimeMillis();
                throw UserFacingException.badGateway("Не вдалося зʼєднатися з %s. Спробуйте пізніше.".formatted(site));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw UserFacingException.badGateway("Не вдалося зʼєднатися з %s. Спробуйте пізніше.".formatted(site));
            }
        }
    }

    /** The site without subdomains: api.syosetu.com and ncode.syosetu.com wait for each other. */
    static String site(String host) {
        String[] parts = host.toLowerCase(java.util.Locale.ROOT).split("\\.");
        return parts.length <= 2 ? host : parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
