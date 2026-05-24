package com.multibot.analytics.application;

import com.multibot.analytics.domain.model.AnalyticsSnapshot;
import com.multibot.analytics.domain.port.in.GetAnalyticsUseCase;
import com.multibot.analytics.domain.port.out.AnalyticsRepository;
import com.multibot.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
class AnalyticsService implements GetAnalyticsUseCase {

    private static final int TOP_QUESTIONS_LIMIT = 10;
    private static final int LONGEST_SESSIONS_LIMIT = 5;
    private static final double COST_PER_TOKEN_USD = 0.000005;

    private final AnalyticsRepository repository;

    AnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsSnapshot get(AnalyticsPeriod period) {
        UUID tenantId = TenantContext.get().value();

        Instant now = Instant.now();
        Instant from = startOfDayUtc(period.days());
        Instant previousFrom = startOfDayUtc(period.days() * 2);
        Instant previousTo = from;

        AnalyticsSnapshot.Overview overview = buildOverview(tenantId, from, now, previousFrom, previousTo);
        AnalyticsSnapshot.Engagement engagement = buildEngagement(tenantId, from, now, previousFrom, previousTo);

        return new AnalyticsSnapshot(
                period.name(),
                overview,
                engagement,
                repository.dailyTimeseries(tenantId, from, now),
                repository.hourlyHeatmap(tenantId, from, now),
                repository.topQuestions(tenantId, from, now, TOP_QUESTIONS_LIMIT),
                repository.versionActivity(tenantId),
                repository.longestSessions(tenantId, from, now, LONGEST_SESSIONS_LIMIT));
    }

    private AnalyticsSnapshot.Overview buildOverview(UUID tenantId, Instant from, Instant to,
                                                     Instant prevFrom, Instant prevTo) {
        long sessions = repository.countSessions(tenantId, from, to);
        long messages = repository.countMessages(tenantId, from, to);
        long tokens = repository.sumTokens(tenantId, from, to);
        long uniqueUsers = repository.countUniqueUsers(tenantId, from, to);

        long prevSessions = repository.countSessions(tenantId, prevFrom, prevTo);
        long prevMessages = repository.countMessages(tenantId, prevFrom, prevTo);
        long prevTokens = repository.sumTokens(tenantId, prevFrom, prevTo);
        long prevUsers = repository.countUniqueUsers(tenantId, prevFrom, prevTo);

        return new AnalyticsSnapshot.Overview(
                sessions, percentDelta(sessions, prevSessions),
                messages, percentDelta(messages, prevMessages),
                tokens, percentDelta(tokens, prevTokens),
                round4(tokens * COST_PER_TOKEN_USD),
                uniqueUsers, percentDelta(uniqueUsers, prevUsers),
                round1(repository.returningUsersPercent(tenantId, from, to)));
    }

    private AnalyticsSnapshot.Engagement buildEngagement(UUID tenantId, Instant from, Instant to,
                                                         Instant prevFrom, Instant prevTo) {
        return new AnalyticsSnapshot.Engagement(
                round1(repository.avgMessagesPerSession(tenantId, from, to)),
                round1(repository.avgMessagesPerSession(tenantId, prevFrom, prevTo)),
                repository.avgSessionDurationSeconds(tenantId, from, to));
    }

    private static Instant startOfDayUtc(int daysAgo) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo).atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC);
    }

    private static double percentDelta(long current, long previous) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return round1(100.0 * (current - previous) / previous);
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
