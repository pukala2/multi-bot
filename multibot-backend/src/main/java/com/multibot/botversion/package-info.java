@ApplicationModule(
        displayName = "Chatbot Versions",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "api"}
)
package com.multibot.botversion;

import org.springframework.modulith.ApplicationModule;
