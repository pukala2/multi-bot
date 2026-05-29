@ApplicationModule(
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "tenant", "security", "api"}
)
package com.multibot.auth;

import org.springframework.modulith.ApplicationModule;
