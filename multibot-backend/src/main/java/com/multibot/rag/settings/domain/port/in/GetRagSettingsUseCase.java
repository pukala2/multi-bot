package com.multibot.rag.settings.domain.port.in;

import com.multibot.rag.settings.domain.model.RagSettings;

public interface GetRagSettingsUseCase {

    RagSettings getForCurrentTenant();
}
