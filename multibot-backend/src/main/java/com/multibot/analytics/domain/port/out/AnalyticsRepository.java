package com.multibot.analytics.domain.port.out;

import com.multibot.analytics.domain.model.AnalyticsSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalyticsRepository {

    long countSessions(UUID tenantId, Instant from, Instant to);

    long countMessages(UUID tenantId, Instant from, Instant to);

    long sumTokens(UUID tenantId, Instant from, Instant to);

    long countUniqueUsers(UUID tenantId, Instant from, Instant to);

    double returningUsersPercent(UUID tenantId, Instant from, Instant to);

    double avgMessagesPerSession(UUID tenantId, Instant from, Instant to);

    long avgSessionDurationSeconds(UUID tenantId, Instant from, Instant to);

    List<AnalyticsSnapshot.DailyPoint> dailyTimeseries(UUID tenantId, Instant from, Instant to);

    List<AnalyticsSnapshot.HeatmapCell> hourlyHeatmap(UUID tenantId, Instant from, Instant to);

    List<AnalyticsSnapshot.TopQuestion> topQuestions(UUID tenantId, Instant from, Instant to, int limit);

    List<AnalyticsSnapshot.VersionActivity> versionActivity(UUID tenantId);

    List<AnalyticsSnapshot.LongestSession> longestSessions(UUID tenantId, Instant from, Instant to, int limit);
}
