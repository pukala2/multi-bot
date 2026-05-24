package com.multibot.analytics.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AnalyticsSnapshot(
        String period,
        Overview overview,
        Engagement engagement,
        List<DailyPoint> dailyTimeseries,
        List<HeatmapCell> hourlyHeatmap,
        List<TopQuestion> topQuestions,
        List<VersionActivity> versionActivity,
        List<LongestSession> longestSessions) {

    public record Overview(
            long sessions,
            double sessionsDeltaPercent,
            long messages,
            double messagesDeltaPercent,
            long tokens,
            double tokensDeltaPercent,
            double estimatedCostUsd,
            long uniqueUsers,
            double uniqueUsersDeltaPercent,
            double returningUsersPercent) {}

    public record Engagement(
            double avgMessagesPerSession,
            double previousAvgMessagesPerSession,
            long avgSessionDurationSeconds) {}

    public record DailyPoint(LocalDate date, long sessions, long messages, long tokens) {}

    public record HeatmapCell(int dayOfWeek, int hour, long count) {}

    public record TopQuestion(String content, long occurrences) {}

    public record VersionActivity(UUID versionId, String name, String status, long sessionCount) {}

    public record LongestSession(
            UUID sessionId,
            String anonymousId,
            long messageCount,
            long durationSeconds,
            long tokensUsed,
            java.time.Instant lastActivityAt) {}
}
