package com.multibot.botversion.domain.port.out;

import com.multibot.botversion.domain.model.ChatbotVersion;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ChatbotVersionRepository {

    List<ChatbotVersion> findAllByTenant(UUID tenantId);

    Optional<ChatbotVersion> findById(UUID versionId, UUID tenantId);

    Optional<ChatbotVersion> findActive(UUID tenantId);

    Optional<UUID> findActiveVersionId(UUID tenantId);

    void insertDraft(UUID id, UUID tenantId, String name);

    void copyDocuments(UUID fromVersionId, UUID toVersionId);

    void deleteVersion(UUID versionId);

    void activate(UUID versionId, UUID tenantId);

    Set<UUID> getDocumentIdsInVersion(UUID versionId);

    void addDocument(UUID versionId, UUID documentId);

    void removeDocument(UUID versionId, UUID documentId);

    boolean isActive(UUID versionId);
}
