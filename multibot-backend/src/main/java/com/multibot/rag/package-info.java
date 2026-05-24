@ApplicationModule(
        displayName = "RAG Orchestrator",
        type = ApplicationModule.Type.OPEN,
        allowedDependencies = {"shared", "tenancy", "ai", "vector", "indexing", "api"}
)
package com.multibot.rag;

import org.springframework.modulith.ApplicationModule;
