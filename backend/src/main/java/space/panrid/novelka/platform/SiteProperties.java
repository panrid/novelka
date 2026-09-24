package space.panrid.novelka.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicUrl address readers open, used in links inside emails, without a trailing slash
 * @param mailFrom  sender of all site emails
 */
@ConfigurationProperties("novelka")
public record SiteProperties(String publicUrl, String mailFrom) {

    public SiteProperties {
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalArgumentException("novelka.public-url is required");
        }
        publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public String link(String path) {
        return publicUrl + path;
    }
}
