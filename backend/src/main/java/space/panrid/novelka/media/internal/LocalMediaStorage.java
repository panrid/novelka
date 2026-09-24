package space.panrid.novelka.media.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Files under {@code novelka.media.dir}, served at {@code /media/}. Names are random and
 * never reused, so browsers may cache them forever. In production the reverse proxy can
 * serve this directory directly.
 */
@Component
class LocalMediaStorage implements MediaStorage, WebMvcConfigurer {

    static final String URL_PREFIX = "/media/";

    private final Path root;

    LocalMediaStorage(@Value("${novelka.media.dir}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void write(String key, byte[] content) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Key escapes the media directory: " + key);
        }
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Override
    public String url(String key) {
        return URL_PREFIX + key;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(URL_PREFIX + "**")
                .addResourceLocations(root.toUri().toString())
                .setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(365))
                        .cachePublic().immutable());
    }
}
