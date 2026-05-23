package com.multibot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String widgetPath;

    public WebConfig(@Value("${multibot.widget.dist-path:../multibot-widget/dist/}") String widgetPath) {
        this.widgetPath = widgetPath;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "null"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "Location")
                .allowCredentials(true);

        registry.addMapping("/widget/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET")
                .maxAge(86400);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/widget/**")
                .addResourceLocations("file:" + widgetPath)
                .setCachePeriod(0);
    }
}
