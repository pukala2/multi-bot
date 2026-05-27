@ApplicationModule(
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "tenant"}
)
package com.multibot.auth;

import org.springframework.modulith.ApplicationModule;
