@ApplicationModule(
        displayName = "Chat",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "api", "document", "rag"}
)
package com.multibot.chat;

import org.springframework.modulith.ApplicationModule;
