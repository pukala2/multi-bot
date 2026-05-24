package com.multibot.botversion.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record ChatbotVersion(
        UUID id,
        UUID tenantId,
        String name,
        VersionStatus status,
        Instant createdAt,
        Optional<Instant> publishedAt,
        long documentCount) {

    public enum VersionStatus {
        DRAFT, ACTIVE, ARCHIVED
    }
}
