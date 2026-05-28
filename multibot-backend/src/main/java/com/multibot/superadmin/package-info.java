@ApplicationModule(
        displayName = "Super Admin (zarzadzanie platforma)",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "tenant", "auth", "billing", "botversion", "widget"}
)
package com.multibot.superadmin;

import org.springframework.modulith.ApplicationModule;
