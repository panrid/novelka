package space.panrid.novelka.platform.web;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app. Pages use ordinary paths ({@code /n/mag-vody/12}), so any
 * path that is not a file and not an API call gets {@code index.html} and the router
 * in the browser takes over.
 */
@Configuration
class WebAppConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Built files carry a hash in their names, so a name never changes its content: a year in
        // the browser's cache. Pages (index.html, which points at them) get no-cache from the security headers.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        if (path.startsWith("api/") || path.equals("api")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
