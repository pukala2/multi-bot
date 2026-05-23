package com.multibot.admin.api;

import com.multibot.admin.domain.model.AdminStats;
import com.multibot.admin.domain.port.in.GetAdminStatsUseCase;
import com.multibot.api.generated.api.AdminApi;
import com.multibot.api.generated.model.AdminStatsResponse;
import com.multibot.api.generated.model.DocumentStats;
import com.multibot.api.generated.model.MessageStats;
import com.multibot.api.generated.model.SessionStats;
import com.multibot.api.generated.model.TokenStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
class AdminStatsController implements AdminApi {

    private final GetAdminStatsUseCase getAdminStatsUseCase;

    AdminStatsController(GetAdminStatsUseCase getAdminStatsUseCase) {
        this.getAdminStatsUseCase = getAdminStatsUseCase;
    }

    @Override
    public ResponseEntity<AdminStatsResponse> getAdminStats() {
        AdminStats stats = getAdminStatsUseCase.getStats();
        return ResponseEntity.ok(toResponse(stats));
    }

    private static AdminStatsResponse toResponse(AdminStats stats) {
        AdminStatsResponse response = new AdminStatsResponse(
                toDocumentStats(stats.documents()),
                toSessionStats(stats.sessions()),
                toMessageStats(stats.messages()),
                toTokenStats(stats.tokens()));
        stats.lastActivity()
                .map(i -> i.atOffset(ZoneOffset.UTC))
                .ifPresent(response::setLastActivity);
        return response;
    }

    private static DocumentStats toDocumentStats(AdminStats.DocumentStats d) {
        return new DocumentStats(d.total(), d.byStatus());
    }

    private static SessionStats toSessionStats(AdminStats.SessionStats s) {
        return new SessionStats(s.total(), s.today(), s.thisWeek());
    }

    private static MessageStats toMessageStats(AdminStats.MessageStats m) {
        return new MessageStats(m.total(), m.today());
    }

    private static TokenStats toTokenStats(AdminStats.TokenStats t) {
        return new TokenStats(t.total(), t.today(), t.estimatedCostUsd());
    }

    @SuppressWarnings("unused")
    private static OffsetDateTime ignore() {
        return null;
    }
}
