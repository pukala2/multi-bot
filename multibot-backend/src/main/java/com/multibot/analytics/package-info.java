@ApplicationModule(
        displayName = "Analytics",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "api"}
)
package com.multibot.analytics;

import org.springframework.modulith.ApplicationModule;
