@ApplicationModule(
        displayName = "Bots (provisioning + self-serve)",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "tenant", "billing", "botversion", "widget", "auth"}
)
package com.multibot.bots;

import org.springframework.modulith.ApplicationModule;
