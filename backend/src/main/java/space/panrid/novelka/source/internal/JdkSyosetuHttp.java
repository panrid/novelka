package space.panrid.novelka.source.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SyosetuHttp;

/**
 * Plain requests to syosetu.com and its API, one at a time with a pause in between so the
 * site is not hammered. The 18+ site needs the «over18» cookie.
 */
@Component
class JdkSyosetuHttp implements SyosetuHttp {

    private static final long PAUSE_MILLIS = 1_000;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private long lastRequest;

    @Override
    public synchronized String get(String url) {
        URI uri = URI.create(url);
        if (!uri.getHost().endsWith("syosetu.com")) {
            throw new IllegalArgumentException("Only syosetu.com: " + url);
        }
        try {
            long wait = lastRequest + PAUSE_MILLIS - System.currentTimeMillis();
            if (wait > 0) {
                Thread.sleep(wait);
            }
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (compatible; Novelka)")
                    .header("Cookie", "over18=yes")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            lastRequest = System.currentTimeMillis();
            if (response.statusCode() == 404) {
                throw UserFacingException.notFound("На Syosetu такої сторінки немає.");
            }
            if (response.statusCode() != 200) {
                throw UserFacingException.badGateway("Syosetu не відповідає як слід (код %d). Спробуйте пізніше."
                        .formatted(response.statusCode()));
            }
            return response.body();
        } catch (IOException error) {
            throw UserFacingException.badGateway("Не вдалося зʼєднатися із Syosetu. Спробуйте пізніше.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw UserFacingException.badGateway("Не вдалося зʼєднатися із Syosetu. Спробуйте пізніше.");
        }
    }
}
