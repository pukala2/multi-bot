package com.multibot.botversion.domain.port.out;

import com.multibot.botversion.domain.model.ChatbotVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatbotVersionRepository {

    List<ChatbotVersion> findAllByTenant(UUID tenantId);

    Optional<ChatbotVersion> findById(UUID versionId, UUID tenantId);

    Optional<ChatbotVersion> findActive(UUID tenantId);

    Optional<UUID> findActiveVersionId(UUID tenantId);

    void insertDraft(UUID id, UUID tenantId, String name);

    void deepCopyVersion(UUID fromVersionId, UUID toVersionId);

    void deleteVersion(UUID versionId);

    void activate(UUID versionId, UUID tenantId);

    boolean isActive(UUID versionId);
}
