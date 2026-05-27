@ApplicationModule(
        displayName = "Chat",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "api", "document", "rag", "billing"}
)
package com.multibot.chat;

import org.springframework.modulith.ApplicationModule;
