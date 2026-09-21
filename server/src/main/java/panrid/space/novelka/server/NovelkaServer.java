package panrid.space.novelka.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class NovelkaServer {
    public static void main(String[] args) {
        SpringApplication.run(NovelkaServer.class, args);
    }
}
