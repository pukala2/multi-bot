@ApplicationModule(
        displayName = "Indexing Pipeline",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "ai", "file", "vector", "document", "billing"}
)
package com.multibot.indexing;

import org.springframework.modulith.ApplicationModule;
