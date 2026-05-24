package com.multibot.rag.settings.domain.port.out;

import com.multibot.rag.settings.domain.model.RagSettings;

import java.util.Optional;
import java.util.UUID;

public interface RagSettingsRepository {

    Optional<RagSettings> find(UUID tenantId);

    void insert(UUID tenantId, RagSettings settings);

    void update(UUID tenantId, RagSettings settings);
}
