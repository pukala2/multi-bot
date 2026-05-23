@ApplicationModule(
        displayName = "Admin Stats",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "api"}
)
package com.multibot.admin;

import org.springframework.modulith.ApplicationModule;
