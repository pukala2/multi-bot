@ApplicationModule(
        displayName = "Super Admin (zarzadzanie platforma)",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "organization", "tenant", "auth", "billing", "bots"}
)
package com.multibot.superadmin;

import org.springframework.modulith.ApplicationModule;
