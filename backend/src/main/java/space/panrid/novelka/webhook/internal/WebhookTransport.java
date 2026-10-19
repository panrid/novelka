package space.panrid.novelka.webhook.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.stereotype.Component;

import space.panrid.novelka.webhook.WebhookAddresses.Target;

/**
 * One POST to a webhook. A plain HTTP/1.1 request over a socket opened to an address
 * {@link space.panrid.novelka.webhook.WebhookAddresses} has checked — not to whatever DNS says a
 * moment later — with TLS verifying the certificate against the name in the address. Redirects
 * are never followed: only the status line is read. Everything, connecting included, fits in
 * {@link #TIMEOUT}.
 */
@Component
class WebhookTransport {

    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final String USER_AGENT = "Novelka-Webhooks/1";
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9.]+|.*:.*");

    /**
     * How an attempt went.
     *
     * @param status the HTTP status the receiver answered, null when it did not answer
     * @param error  why there is no answer
     */
    record Result(Integer status, int millis, String error) {
    }

    Result post(Target target, Map<String, String> headers, byte[] body) {
        long started = System.nanoTime();
        long deadline = started + TIMEOUT.toNanos();
        URI uri = URI.create(target.uri().toASCIIString());
        boolean tls = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() != -1 ? uri.getPort() : tls ? 443 : 80;
        String host = uri.getHost();
        String bareHost = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        String error = "Не вдалося з'єднатися з сервером.";
        for (InetAddress address : target.addresses()) {
            int left = remaining(deadline);
            if (left <= 0) {
                error = "Сервер не відповів за 10 секунд.";
                break;
            }
            Socket raw = new Socket();
            try {
                raw.connect(new InetSocketAddress(address, port), left);
            } catch (SocketTimeoutException timeout) {
                close(raw);
                error = "Сервер не відповів за 10 секунд.";
                continue;
            } catch (IOException refused) {
                close(raw);
                error = "Не вдалося з'єднатися з сервером.";
                continue;
            }
            // Connected: this address answers for the rest; no second try within the attempt.
            try {
                raw.setSoTimeout(Math.max(1, remaining(deadline)));
                Socket socket = tls ? secure(raw, bareHost, port) : raw;
                try {
                    OutputStream out = socket.getOutputStream();
                    out.write(head(uri, host, port, tls, headers, body.length));
                    out.write(body);
                    out.flush();
                    socket.setSoTimeout(Math.max(1, remaining(deadline)));
                    int status = status(socket.getInputStream(), deadline);
                    return new Result(status, millis(started), null);
                } finally {
                    close(socket);
                }
            } catch (SocketTimeoutException timeout) {
                return new Result(null, millis(started), "Сервер не відповів за 10 секунд.");
            } catch (SSLException insecure) {
                return new Result(null, millis(started), "Не вдалося встановити захищене з'єднання: сертифікат не пройшов перевірку.");
            } catch (IOException broken) {
                return new Result(null, millis(started), "З'єднання обірвалося до відповіді.");
            } finally {
                close(raw);
            }
        }
        return new Result(null, millis(started), error);
    }

    /** TLS over the connected socket, the certificate checked against the address's name. */
    private static Socket secure(Socket raw, String host, int port) throws IOException {
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
        SSLParameters parameters = ssl.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        if (!IP_LITERAL.matcher(host).matches()) {
            parameters.setServerNames(List.of(new SNIHostName(host)));
        }
        ssl.setSSLParameters(parameters);
        ssl.startHandshake();
        return ssl;
    }

    private static byte[] head(URI uri, String host, int port, boolean tls, Map<String, String> headers, int length) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String target = uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        StringBuilder head = new StringBuilder()
                .append("POST ").append(target).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append(port == (tls ? 443 : 80) ? "" : ":" + port).append("\r\n")
                .append("User-Agent: ").append(USER_AGENT).append("\r\n")
                .append("Content-Type: application/json; charset=utf-8\r\n")
                .append("Content-Length: ").append(length).append("\r\n")
                .append("Accept: */*\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("Connection: close\r\n\r\n");
        return head.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The status from «HTTP/1.1 200 OK»; the rest of the answer is not read. */
    private static int status(InputStream in, long deadline) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int next;
        while ((next = in.read()) != -1 && next != '\n') {
            if (line.size() > 1024 || remaining(deadline) <= 0) {
                throw new SocketTimeoutException("status line");
            }
            line.write(next);
        }
        String[] parts = line.toString(StandardCharsets.US_ASCII).strip().split(" ");
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new IOException("Not HTTP");
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException error) {
            throw new IOException("Not HTTP", error);
        }
    }

    private static int remaining(long deadline) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (deadline - System.nanoTime()) / 1_000_000));
    }

    private static int millis(long started) {
        return (int) ((System.nanoTime() - started) / 1_000_000);
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing anyway.
        }
    }
}
