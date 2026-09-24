package space.panrid.novelka.account;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = {
        "novelka.owner.nick=panrid",
        "novelka.owner.email=owner@example.com",
        "novelka.owner.password=пароль власника сайту"})
class OwnerBootstrapTests {

    @LocalServerPort
    int port;

    @Test
    void theOwnerIsCreatedConfirmedOnFirstStart() {
        Browser.Response response = new Browser(port).post("/api/auth/login",
                json("login", "panrid", "password", "пароль власника сайту"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"role\":\"owner\"").contains("\"emailVerified\":true");
    }
}
