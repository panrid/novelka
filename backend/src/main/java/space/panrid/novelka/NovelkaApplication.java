package space.panrid.novelka;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NovelkaApplication {

    public static void main(String[] args) {
        // All timestamps are stored and compared in UTC; the browser formats them for the reader.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(NovelkaApplication.class, args);
    }
}
