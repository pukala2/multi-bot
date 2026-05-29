@ApplicationModule(
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "api"}
)
package com.multibot.billing;

import org.springframework.modulith.ApplicationModule;
