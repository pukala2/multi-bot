@ApplicationModule(
        displayName = "Organization (klient platformy)",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy"}
)
package com.multibot.organization;

import org.springframework.modulith.ApplicationModule;
