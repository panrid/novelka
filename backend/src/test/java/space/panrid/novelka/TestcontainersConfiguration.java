package space.panrid.novelka;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.FakeSyosetu;
import space.panrid.novelka.support.TestMailbox;

/** A real PostgreSQL 17, a mailbox that keeps letters, and Syosetu and the model faked. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }

    @Bean
    @Primary
    TestMailbox testMailbox() {
        return new TestMailbox();
    }

    @Bean
    @Primary
    FakeSyosetu fakeSyosetu() {
        return new FakeSyosetu();
    }

    @Bean
    @Primary
    FakeModel fakeModel() {
        return new FakeModel();
    }
}
