package com.multibot.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multibot.security")
public record SecurityShieldProperties(
        RateLimit rateLimit,
        Turnstile turnstile
) {

    public record RateLimit(
            int perIpPerDay,
            int perSessionPerDay
    ) {
    }

    public record Turnstile(
            boolean enabled,
            String siteKey,
            String secretKey
    ) {
    }
}
