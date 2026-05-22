@ApplicationModule(
        displayName = "Tenant",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy"}
)
package com.multibot.tenant;

import org.springframework.modulith.ApplicationModule;
