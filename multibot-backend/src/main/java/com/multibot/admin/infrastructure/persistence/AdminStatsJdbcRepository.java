package com.multibot.admin.infrastructure.persistence;

import com.multibot.admin.domain.port.out.AdminStatsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class AdminStatsJdbcRepository implements AdminStatsRepository {

    private static final String[] DOCUMENT_STATUSES = {"PENDING", "PROCESSING", "READY", "FAILED"};

    private final JdbcTemplate jdbc;

    AdminStatsJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countDocuments(UUID tenantId) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE tenant_id = ?",
                Long.class, tenantId));
    }

    @Override
    public Map<String, Long> countDocumentsByStatus(UUID tenantId) {
        Map<String, Long> result = new HashMap<>();
        for (String status : DOCUMENT_STATUSES) {
            result.put(status, 0L);
        }
        jdbc.query(
                "SELECT status, COUNT(*) FROM documents WHERE tenant_id = ? GROUP BY status",
                rs -> { result.put(rs.getString(1), rs.getLong(2)); },
                tenantId);
        return result;
    }

    @Override
    public long countSessions(UUID tenantId) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE tenant_id = ?",
                Long.class, tenantId));
    }

    @Override
    public long countSessionsSince(UUID tenantId, Instant since) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE tenant_id = ? AND started_at >= ?",
                Long.class, tenantId, Timestamp.from(since)));
    }

    @Override
    public long countMessages(UUID tenantId) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE tenant_id = ?",
                Long.class, tenantId));
    }

    @Override
    public long countMessagesSince(UUID tenantId, Instant since) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE tenant_id = ? AND created_at >= ?",
                Long.class, tenantId, Timestamp.from(since)));
    }

    @Override
    public long sumTokens(UUID tenantId) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COALESCE(SUM(tokens_used), 0) FROM messages WHERE tenant_id = ? AND tokens_used IS NOT NULL",
                Long.class, tenantId));
    }

    @Override
    public long sumTokensSince(UUID tenantId, Instant since) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COALESCE(SUM(tokens_used), 0) FROM messages WHERE tenant_id = ? AND tokens_used IS NOT NULL AND created_at >= ?",
                Long.class, tenantId, Timestamp.from(since)));
    }

    @Override
    public Optional<Instant> findLastActivity(UUID tenantId) {
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

    private static long nullToZero(Long v) {
        return v != null ? v : 0L;
    }
}
