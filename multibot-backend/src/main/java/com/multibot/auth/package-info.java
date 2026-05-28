@ApplicationModule(
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "tenant", "security"}
)
package com.multibot.auth;

import org.springframework.modulith.ApplicationModule;
