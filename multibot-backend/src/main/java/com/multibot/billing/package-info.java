@ApplicationModule(
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy"}
)
package com.multibot.billing;

import org.springframework.modulith.ApplicationModule;
