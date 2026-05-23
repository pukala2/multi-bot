package com.multibot.admin.application;

import com.multibot.admin.domain.model.AdminStats;
import com.multibot.admin.domain.port.in.GetAdminStatsUseCase;
import com.multibot.admin.domain.port.out.AdminStatsRepository;
import com.multibot.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
class AdminStatsService implements GetAdminStatsUseCase {

    private static final double COST_PER_TOKEN_USD = 0.000005;

    private final AdminStatsRepository repository;

    AdminStatsService(AdminStatsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStats getStats() {
        UUID tenantId = TenantContext.get().value();
        Instant startOfToday = startOfDay(0);
        Instant startOfWeek = startOfDay(7);

        return new AdminStats(
                documentStats(tenantId),
                sessionStats(tenantId, startOfToday, startOfWeek),
                messageStats(tenantId, startOfToday),
                tokenStats(tenantId, startOfToday),
                repository.findLastActivity(tenantId));
    }

    private AdminStats.DocumentStats documentStats(UUID tenantId) {
        return new AdminStats.DocumentStats(
                repository.countDocuments(tenantId),
                repository.countDocumentsByStatus(tenantId));
    }

    private AdminStats.SessionStats sessionStats(UUID tenantId, Instant today, Instant week) {
        return new AdminStats.SessionStats(
                repository.countSessions(tenantId),
                repository.countSessionsSince(tenantId, today),
                repository.countSessionsSince(tenantId, week));
    }

    private AdminStats.MessageStats messageStats(UUID tenantId, Instant today) {
        return new AdminStats.MessageStats(
                repository.countMessages(tenantId),
                repository.countMessagesSince(tenantId, today));
    }

    private AdminStats.TokenStats tokenStats(UUID tenantId, Instant today) {
        long total = repository.sumTokens(tenantId);
        long todayTokens = repository.sumTokensSince(tenantId, today);
        double estimatedCost = round4(todayTokens * COST_PER_TOKEN_USD);
        return new AdminStats.TokenStats(total, todayTokens, estimatedCost);
    }

    private static Instant startOfDay(int daysAgo) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo);
        return LocalDateTime.of(date, LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
