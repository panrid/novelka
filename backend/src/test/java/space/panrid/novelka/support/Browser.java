package space.panrid.novelka.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * A real HTTP client with its own cookie jar, like one browser tab: keeps the session
 * cookie and answers CSRF the way the web app does (cookie XSRF-TOKEN → header).
 */
public class Browser {

    public record Response(int status, String body) {
    }

    public record BinaryResponse(int status, String contentType, byte[] body) {
    }

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;

    public Browser(int port) {
        this.base = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    public Response post(String path, String json) {
        if (csrf().isEmpty()) {
            get("/api/me"); // any response sets the XSRF-TOKEN cookie
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        csrf().ifPresent(token -> request.header("X-XSRF-TOKEN", token));
        return send(request);
    }

    public Response patch(String path, String json) {
        return change("PATCH", path, "application/json", HttpRequest.BodyPublishers.ofString(json));
    }

    public Response put(String path, String json) {
        return change("PUT", path, "application/json", HttpRequest.BodyPublishers.ofString(json));
    }

    public Response delete(String path) {
        return change("DELETE", path, "application/json", HttpRequest.BodyPublishers.noBody());
    }

    /** A multipart/form-data upload of one file, like an <input type=file>. */
    public Response upload(String path, String field, String filename, String contentType, byte[] content) {
        return upload(path, java.util.Map.of(), field, filename, contentType, content);
    }

    /** A form with text fields and one file. */
    public Response upload(String path, java.util.Map<String, String> fields, String field, String filename,
            String contentType, byte[] content) {
        String boundary = "----novelka" + System.nanoTime();
        StringBuilder text = new StringBuilder();
        fields.forEach((name, value) -> text.append("--").append(boundary)
                .append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n"));
        byte[] head = (text + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\""
                + filename + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        return change("POST", path, "multipart/form-data; boundary=" + boundary,
                HttpRequest.BodyPublishers.ofByteArray(body));
    }

    public BinaryResponse download(String path) {
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""), response.body());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private Response change(String method, String path, String contentType, HttpRequest.BodyPublisher body) {
        if (csrf().isEmpty()) {
            get("/api/me");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", contentType)
                .method(method, body);
        csrf().ifPresent(token -> request.header("X-XSRF-TOKEN", token));
        return send(request);
    }

    public Response postWithoutCsrf(String path, String json) {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private Optional<String> csrf() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.header("Accept", "application/json").build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Minimal JSON string escaping for request bodies in tests. */
    public static String json(Object... keyValues) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(keyValues[i]).append("\":");
            Object value = keyValues[i + 1];
            if (value instanceof String text) {
                out.append('"').append(text.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else {
                out.append(value);
            }
        }
        return out.append('}').toString();
    }
}
