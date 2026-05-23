@ApplicationModule(
        displayName = "Document Management",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "file", "vector", "api", "indexing"}
)
package com.multibot.document;

import org.springframework.modulith.ApplicationModule;
