package com.multibot.admin.application;

import com.multibot.admin.domain.model.AdminStats;
import com.multibot.admin.domain.port.in.GetAdminStatsUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
class AdminStatsService implements GetAdminStatsUseCase {

    private static final double COST_PER_TOKEN_USD = 0.000005;

    private final JdbcTemplate jdbc;

    AdminStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStats getStats() {
        UUID tenantId = TenantContext.get().value();
        Timestamp startOfToday = startOfDay(0);
        Timestamp startOfWeek = startOfDay(7);

        return new AdminStats(
                documentStats(tenantId),
                sessionStats(tenantId, startOfToday, startOfWeek),
                messageStats(tenantId, startOfToday),
                tokenStats(tenantId, startOfToday),
                lastActivity(tenantId));
    }

    private AdminStats.DocumentStats documentStats(UUID tenantId) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE tenant_id = ?",
                Long.class, tenantId);
        Map<String, Long> byStatus = new HashMap<>();
        for (String status : new String[]{"PENDING", "PROCESSING", "READY", "FAILED"}) {
            byStatus.put(status, 0L);
        }
        jdbc.query(
                "SELECT status, COUNT(*) FROM documents WHERE tenant_id = ? GROUP BY status",
                rs -> { byStatus.put(rs.getString(1), rs.getLong(2)); },
                tenantId);
        return new AdminStats.DocumentStats(total != null ? total : 0L, byStatus);
    }

    private AdminStats.SessionStats sessionStats(UUID tenantId, Timestamp today, Timestamp week) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE tenant_id = ?",
                Long.class, tenantId);
        Long todayCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE tenant_id = ? AND started_at >= ?",
                Long.class, tenantId, today);
        Long weekCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE tenant_id = ? AND started_at >= ?",
                Long.class, tenantId, week);
        return new AdminStats.SessionStats(
                nullToZero(total), nullToZero(todayCount), nullToZero(weekCount));
    }

    private AdminStats.MessageStats messageStats(UUID tenantId, Timestamp today) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE tenant_id = ?",
                Long.class, tenantId);
        Long todayCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE tenant_id = ? AND created_at >= ?",
                Long.class, tenantId, today);
        return new AdminStats.MessageStats(nullToZero(total), nullToZero(todayCount));
    }

    private AdminStats.TokenStats tokenStats(UUID tenantId, Timestamp today) {
        Long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(tokens_used), 0) FROM messages WHERE tenant_id = ? AND tokens_used IS NOT NULL",
                Long.class, tenantId);
        Long todayCount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(tokens_used), 0) FROM messages WHERE tenant_id = ? AND tokens_used IS NOT NULL AND created_at >= ?",
                Long.class, tenantId, today);
        long totalTokens = nullToZero(total);
        long todayTokens = nullToZero(todayCount);
        double estimatedCost = todayTokens * COST_PER_TOKEN_USD;
        return new AdminStats.TokenStats(totalTokens, todayTokens, round4(estimatedCost));
    }

    private Optional<Instant> lastActivity(UUID tenantId) {
        Timestamp ts = jdbc.queryForObject(
                """
                SELECT MAX(activity) AS activity FROM (
                  SELECT MAX(uploaded_at) AS activity FROM documents WHERE tenant_id = ?
                  UNION ALL
                  SELECT MAX(last_activity_at) AS activity FROM chat_sessions WHERE tenant_id = ?
                ) AS combined
                """,
                Timestamp.class, tenantId, tenantId);
        return Optional.ofNullable(ts).map(Timestamp::toInstant);
    }

    private static Timestamp startOfDay(int daysAgo) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo);
        LocalDateTime midnight = LocalDateTime.of(date, LocalTime.MIDNIGHT);
        return Timestamp.from(midnight.toInstant(ZoneOffset.UTC));
    }

    private static long nullToZero(Long v) {
        return v != null ? v : 0L;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
