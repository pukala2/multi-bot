@ApplicationModule(
        displayName = "File Storage",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy"}
)
package com.multibot.file;

import org.springframework.modulith.ApplicationModule;
