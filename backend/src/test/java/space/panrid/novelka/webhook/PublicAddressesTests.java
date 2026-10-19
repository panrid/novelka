package space.panrid.novelka.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import space.panrid.novelka.platform.web.UserFacingException;

/** Етап 22.5: webhooks go only to public https addresses — the SSRF guard, without the network. */
class PublicAddressesTests {

    /** Names answered here; IP literals as Java reads them; any other name is not found. */
    private final PublicAddresses guard = new PublicAddresses(host -> switch (host) {
        case "hooks.example" -> new InetAddress[] {ip("93.184.216.34")};
        case "rebind.example" -> new InetAddress[] {ip("93.184.216.34"), ip("10.0.0.7")};
        case "metadata.example" -> new InetAddress[] {ip("169.254.169.254")};
        case "loopback.example" -> new InetAddress[] {ip("127.0.0.1")};
        case "v6.example" -> new InetAddress[] {ip("fd12:3456::1")};
        default -> {
            if (host.matches("[0-9.]+") || host.contains(":")) {
                yield InetAddress.getAllByName(host);
            }
            throw new UnknownHostException(host);
        }
    });

    @Test
    void aPublicHttpsAddressResolvesToTheAddressesToConnectTo() {
        WebhookAddresses.Target target = guard.resolve("  https://hooks.example/novelka?from=site ");
        assertThat(target.uri().toString()).isEqualTo("https://hooks.example/novelka?from=site");
        assertThat(target.addresses()).containsExactly(ip("93.184.216.34"));
        assertThat(guard.resolve("https://hooks.example:8443/x").addresses()).hasSize(1);
        assertThat(guard.resolve("https://93.184.216.34/x").addresses()).containsExactly(ip("93.184.216.34"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://hooks.example/x", "ftp://hooks.example/x", "hooks.example/x", "//hooks.example/x"})
    void onlyHttps(String url) {
        assertThatThrownBy(() -> guard.resolve(url)).isInstanceOf(UserFacingException.class).hasMessageContaining("https://");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://user:secret@hooks.example/x", "https://hooks.example:22/x", "https://hooks.example/x#part",
            "https://hooks example/x", ""})
    void malformedOrSuspiciousAddressesAreRefused(String url) {
        assertThatThrownBy(() -> guard.resolve(url)).isInstanceOf(UserFacingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://localhost/x", "https://api.localhost/x", "https://printer.local/x", "https://db.internal/x", "https://intranet/x",
            "https://127.0.0.1/x", "https://127.1.2.3/x", "https://10.0.0.1/x", "https://172.16.5.4/x", "https://172.31.255.1/x",
            "https://192.168.1.1/x", "https://169.254.169.254/latest/meta-data", "https://100.64.0.1/x", "https://0.0.0.0/x",
            "https://224.0.0.1/x", "https://198.18.0.1/x", "https://192.0.2.10/x",
            "https://[::1]/x", "https://[::]/x", "https://[fd00::1]/x", "https://[fe80::1]/x", "https://[ff02::1]/x",
            "https://[::ffff:127.0.0.1]/x", "https://[::ffff:a9fe:a9fe]/x", "https://[64:ff9b::a00:1]/x", "https://[2002:a00:1::1]/x",
            "https://[2001:db8::1]/x"})
    void addressesInsideAreRefused(String url) {
        assertThatThrownBy(() -> guard.resolve(url)).isInstanceOf(UserFacingException.class).hasMessageContaining("внутрішню мережу");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://metadata.example/x", "https://loopback.example/x", "https://v6.example/x",
            "https://rebind.example/x"})
    void aNameThatResolvesInsideIsRefusedEvenWhenOnlyOneOfItsAddressesIs(String url) {
        assertThatThrownBy(() -> guard.resolve(url)).isInstanceOf(UserFacingException.class).hasMessageContaining("внутрішню мережу");
    }

    @Test
    void anUnknownNameIsNotFound() {
        assertThatThrownBy(() -> guard.resolve("https://nowhere.example/x")).isInstanceOf(UserFacingException.class)
                .hasMessageContaining("не знайдено");
    }

    @Test
    void publicUnicastIsAllowed() {
        assertThat(PublicAddresses.isPublic(ip("8.8.8.8"))).isTrue();
        assertThat(PublicAddresses.isPublic(ip("2606:4700:4700::1111"))).isTrue();
        assertThat(PublicAddresses.isPublic(ip("172.32.0.1"))).isTrue();
        assertThat(PublicAddresses.isPublic(ip("100.128.0.1"))).isTrue();
    }

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
