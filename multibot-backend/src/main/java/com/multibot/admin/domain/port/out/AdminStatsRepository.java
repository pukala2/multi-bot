package com.multibot.admin.domain.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AdminStatsRepository {

    long countDocuments(UUID tenantId);

    Map<String, Long> countDocumentsByStatus(UUID tenantId);

    long countSessions(UUID tenantId);

    long countSessionsSince(UUID tenantId, Instant since);

    long countMessages(UUID tenantId);

    long countMessagesSince(UUID tenantId, Instant since);

    long sumTokens(UUID tenantId);

    long sumTokensSince(UUID tenantId, Instant since);

    Optional<Instant> findLastActivity(UUID tenantId);
}
