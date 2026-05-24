@ApplicationModule(
        displayName = "Vector Store",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "botversion"}
)
package com.multibot.vector;

import org.springframework.modulith.ApplicationModule;
