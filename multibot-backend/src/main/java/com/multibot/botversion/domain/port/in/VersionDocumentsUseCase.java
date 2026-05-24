package com.multibot.botversion.domain.port.in;

import java.util.Set;
import java.util.UUID;

public interface VersionDocumentsUseCase {

    Set<UUID> getDocumentIdsInVersion(UUID versionId);

    void addDocument(UUID versionId, UUID documentId);

    void removeDocument(UUID versionId, UUID documentId);
}
