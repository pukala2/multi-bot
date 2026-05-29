@ApplicationModule(
        displayName = "Security (widget guard, rate limit, Turnstile)",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "widget", "api"}
)
package com.multibot.security;

import org.springframework.modulith.ApplicationModule;
