@ApplicationModule(
        displayName = "Widget Configuration",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "api"}
)
package com.multibot.widget;

import org.springframework.modulith.ApplicationModule;
