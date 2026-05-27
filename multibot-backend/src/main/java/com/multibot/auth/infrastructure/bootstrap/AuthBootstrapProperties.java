package com.multibot.auth.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multibot.bootstrap")
public record AuthBootstrapProperties(
        SuperAdmin superadmin,
        DefaultTenant defaultTenant
) {

    public record SuperAdmin(String email, String password) {
    }

    public record DefaultTenant(boolean enabled, String ownerEmail, String ownerPassword) {
    }
}
