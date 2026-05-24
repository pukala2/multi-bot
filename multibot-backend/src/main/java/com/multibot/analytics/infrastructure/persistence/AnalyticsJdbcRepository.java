package com.multibot.analytics.infrastructure.persistence;

import com.multibot.analytics.domain.model.AnalyticsSnapshot;
import com.multibot.analytics.domain.port.out.AnalyticsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class AnalyticsJdbcRepository implements AnalyticsRepository {

    private final JdbcTemplate jdbc;

    AnalyticsJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countSessions(UUID tenantId, Instant from, Instant to) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE tenant_id = ? AND started_at >= ? AND started_at < ?",
                Long.class, tenantId, ts(from), ts(to)));
    }

    @Override
    public long countMessages(UUID tenantId, Instant from, Instant to) {
        return nullToZero(jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE tenant_id = ? AND created_at >= ? AND created_at < ?",
                Long.class, tenantId, ts(from), ts(to)));
    }

    @Override
    public long sumTokens(UUID tenantId, Instant from, Instant to) {
        return nullToZero(jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(tokens_used), 0) FROM messages
                WHERE tenant_id = ? AND tokens_used IS NOT NULL
                  AND created_at >= ? AND created_at < ?
                """,
                Long.class, tenantId, ts(from), ts(to)));
    }

    @Override
    public long countUniqueUsers(UUID tenantId, Instant from, Instant to) {
        return nullToZero(jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT anonymous_id) FROM chat_sessions
                WHERE tenant_id = ? AND started_at >= ? AND started_at < ?
                """,
                Long.class, tenantId, ts(from), ts(to)));
    }

    @Override
    public double returningUsersPercent(UUID tenantId, Instant from, Instant to) {
        Double percent = jdbc.queryForObject("""
                WITH s AS (
                    SELECT anonymous_id, COUNT(*) AS session_count
                    FROM chat_sessions
                    WHERE tenant_id = ? AND started_at >= ? AND started_at < ?
                    GROUP BY anonymous_id
                )
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE 100.0 * COUNT(*) FILTER (WHERE session_count > 1) / COUNT(*)
                       END
                FROM s
                """, Double.class, tenantId, ts(from), ts(to));
        return percent != null ? percent : 0.0;
    }

    @Override
    public double avgMessagesPerSession(UUID tenantId, Instant from, Instant to) {
        Double avg = jdbc.queryForObject("""
                WITH s AS (
                    SELECT s.id, COUNT(m.id) AS msg_count
                    FROM chat_sessions s
                    LEFT JOIN messages m ON m.session_id = s.id
                    WHERE s.tenant_id = ? AND s.started_at >= ? AND s.started_at < ?
                    GROUP BY s.id
                )
                SELECT COALESCE(AVG(msg_count), 0) FROM s
                """, Double.class, tenantId, ts(from), ts(to));
        return avg != null ? avg : 0.0;
    }

    @Override
    public long avgSessionDurationSeconds(UUID tenantId, Instant from, Instant to) {
        Double avg = jdbc.queryForObject("""
                SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (last_activity_at - started_at))), 0)
                FROM chat_sessions
                WHERE tenant_id = ? AND started_at >= ? AND started_at < ?
                  AND last_activity_at > started_at
                """, Double.class, tenantId, ts(from), ts(to));
        return avg != null ? Math.round(avg) : 0L;
    }

    @Override
    public List<AnalyticsSnapshot.DailyPoint> dailyTimeseries(UUID tenantId, Instant from, Instant to) {
        return jdbc.query("""
                WITH days AS (
                    SELECT generate_series(?::date, ?::date - INTERVAL '1 day', INTERVAL '1 day')::date AS day
                ),
                s_count AS (
                    SELECT DATE(started_at AT TIME ZONE 'UTC') AS day, COUNT(*) AS c
                    FROM chat_sessions WHERE tenant_id = ? AND started_at >= ? AND started_at < ?
                    GROUP BY 1
                ),
                m_count AS (
                    SELECT DATE(created_at AT TIME ZONE 'UTC') AS day, COUNT(*) AS c,
                           COALESCE(SUM(tokens_used), 0) AS tok
                    FROM messages WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                    GROUP BY 1
                )
                SELECT d.day,
                       COALESCE(s_count.c, 0) AS sessions,
                       COALESCE(m_count.c, 0) AS messages,
                       COALESCE(m_count.tok, 0) AS tokens
                FROM days d
                LEFT JOIN s_count ON s_count.day = d.day
                LEFT JOIN m_count ON m_count.day = d.day
                ORDER BY d.day
                """,
                (rs, n) -> new AnalyticsSnapshot.DailyPoint(
                        rs.getDate("day").toLocalDate(),
                        rs.getLong("sessions"),
                        rs.getLong("messages"),
                        rs.getLong("tokens")),
                ts(from), ts(to),
                tenantId, ts(from), ts(to),
                tenantId, ts(from), ts(to));
    }

    @Override
    public List<AnalyticsSnapshot.HeatmapCell> hourlyHeatmap(UUID tenantId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT EXTRACT(ISODOW FROM created_at AT TIME ZONE 'UTC')::int AS day_of_week,
                       EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC')::int AS hour,
                       COUNT(*) AS c
                FROM messages
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY 1, 2
                ORDER BY 1, 2
                """,
                (rs, n) -> new AnalyticsSnapshot.HeatmapCell(
                        rs.getInt("day_of_week"),
                        rs.getInt("hour"),
                        rs.getLong("c")),
                tenantId, ts(from), ts(to));
    }

    @Override
    public List<AnalyticsSnapshot.TopQuestion> topQuestions(UUID tenantId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT content, COUNT(*) AS occurrences
                FROM messages
                WHERE tenant_id = ? AND role = 'USER'
                  AND created_at >= ? AND created_at < ?
                  AND LENGTH(content) BETWEEN 4 AND 200
                GROUP BY content
                ORDER BY occurrences DESC, content
                LIMIT ?
                """,
                (rs, n) -> new AnalyticsSnapshot.TopQuestion(
                        rs.getString("content"),
                        rs.getLong("occurrences")),
                tenantId, ts(from), ts(to), limit);
    }

    @Override
    public List<AnalyticsSnapshot.VersionActivity> versionActivity(UUID tenantId) {
        return jdbc.query("""
                SELECT v.id, v.name, v.status,
                       (SELECT COUNT(*) FROM chat_sessions s
                          WHERE s.tenant_id = v.tenant_id
                            AND s.started_at >= COALESCE(v.published_at, v.created_at)
                            AND (v.status != 'ARCHIVED' OR s.started_at <
                                 COALESCE((SELECT MIN(v2.published_at) FROM chatbot_versions v2
                                            WHERE v2.tenant_id = v.tenant_id
                                              AND v2.published_at > v.published_at), NOW()))
                       ) AS session_count
                FROM chatbot_versions v
                WHERE v.tenant_id = ?
                ORDER BY COALESCE(v.published_at, v.created_at) DESC
                """,
                (rs, n) -> new AnalyticsSnapshot.VersionActivity(
                        (UUID) rs.getObject("id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getLong("session_count")),
                tenantId);
    }

    @Override
    public List<AnalyticsSnapshot.LongestSession> longestSessions(UUID tenantId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT s.id, s.anonymous_id, s.last_activity_at,
                       COUNT(m.id) AS message_count,
                       EXTRACT(EPOCH FROM (s.last_activity_at - s.started_at))::bigint AS duration_seconds,
                       COALESCE(SUM(m.tokens_used), 0) AS tokens_used
                FROM chat_sessions s
                LEFT JOIN messages m ON m.session_id = s.id
                WHERE s.tenant_id = ? AND s.started_at >= ? AND s.started_at < ?
                GROUP BY s.id, s.anonymous_id, s.last_activity_at, s.started_at
                HAVING COUNT(m.id) > 0
                ORDER BY message_count DESC, duration_seconds DESC
                LIMIT ?
                """,
                (rs, n) -> new AnalyticsSnapshot.LongestSession(
                        (UUID) rs.getObject("id"),
                        rs.getString("anonymous_id"),
                        rs.getLong("message_count"),
                        rs.getLong("duration_seconds"),
                        rs.getLong("tokens_used"),
                        rs.getTimestamp("last_activity_at").toInstant()),
                tenantId, ts(from), ts(to), limit);
    }

    private static Timestamp ts(Instant i) { return Timestamp.from(i); }
    private static long nullToZero(Long v) { return v != null ? v : 0L; }
}
