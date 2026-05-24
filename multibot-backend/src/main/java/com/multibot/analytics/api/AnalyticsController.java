package com.multibot.analytics.api;

import com.multibot.analytics.domain.model.AnalyticsSnapshot;
import com.multibot.analytics.domain.port.in.GetAnalyticsUseCase;
import com.multibot.api.generated.api.AnalyticsApi;
import com.multibot.api.generated.model.AnalyticsDailyPoint;
import com.multibot.api.generated.model.AnalyticsEngagement;
import com.multibot.api.generated.model.AnalyticsHeatmapCell;
import com.multibot.api.generated.model.AnalyticsLongestSession;
import com.multibot.api.generated.model.AnalyticsOverview;
import com.multibot.api.generated.model.AnalyticsSnapshotResponse;
import com.multibot.api.generated.model.AnalyticsTopQuestion;
import com.multibot.api.generated.model.AnalyticsVersionActivity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

@RestController
class AnalyticsController implements AnalyticsApi {

    private final GetAnalyticsUseCase getAnalyticsUseCase;

    AnalyticsController(GetAnalyticsUseCase getAnalyticsUseCase) {
        this.getAnalyticsUseCase = getAnalyticsUseCase;
    }

    @Override
    public ResponseEntity<AnalyticsSnapshotResponse> getAnalytics(String periodStr) {
        GetAnalyticsUseCase.AnalyticsPeriod period = parsePeriod(periodStr);
        AnalyticsSnapshot snapshot = getAnalyticsUseCase.get(period);
        return ResponseEntity.ok(toResponse(snapshot));
    }

    private static GetAnalyticsUseCase.AnalyticsPeriod parsePeriod(String periodStr) {
        if (periodStr == null || periodStr.isBlank()) return GetAnalyticsUseCase.AnalyticsPeriod.D7;
        try {
            return GetAnalyticsUseCase.AnalyticsPeriod.valueOf(periodStr);
        } catch (IllegalArgumentException e) {
            return GetAnalyticsUseCase.AnalyticsPeriod.D7;
        }
    }

    private static AnalyticsSnapshotResponse toResponse(AnalyticsSnapshot s) {
        AnalyticsSnapshotResponse response = new AnalyticsSnapshotResponse(
                s.period(),
                toOverview(s.overview()),
                toEngagement(s.engagement()),
                s.dailyTimeseries().stream().map(AnalyticsController::toDaily).toList(),
                s.hourlyHeatmap().stream().map(AnalyticsController::toHeatmap).toList(),
                s.topQuestions().stream().map(AnalyticsController::toTopQuestion).toList(),
                s.versionActivity().stream().map(AnalyticsController::toVersion).toList(),
                s.longestSessions().stream().map(AnalyticsController::toLongest).toList());
        return response;
    }

    private static AnalyticsOverview toOverview(AnalyticsSnapshot.Overview o) {
        return new AnalyticsOverview(
                o.sessions(), o.sessionsDeltaPercent(),
                o.messages(), o.messagesDeltaPercent(),
                o.tokens(), o.tokensDeltaPercent(),
                o.estimatedCostUsd(),
                o.uniqueUsers(), o.uniqueUsersDeltaPercent(),
                o.returningUsersPercent());
    }

    private static AnalyticsEngagement toEngagement(AnalyticsSnapshot.Engagement e) {
        return new AnalyticsEngagement(
                e.avgMessagesPerSession(),
                e.previousAvgMessagesPerSession(),
                e.avgSessionDurationSeconds());
    }

    private static AnalyticsDailyPoint toDaily(AnalyticsSnapshot.DailyPoint p) {
        return new AnalyticsDailyPoint(p.date(), p.sessions(), p.messages(), p.tokens());
    }

    private static AnalyticsHeatmapCell toHeatmap(AnalyticsSnapshot.HeatmapCell c) {
        return new AnalyticsHeatmapCell(c.dayOfWeek(), c.hour(), c.count());
    }

    private static AnalyticsTopQuestion toTopQuestion(AnalyticsSnapshot.TopQuestion q) {
        return new AnalyticsTopQuestion(q.content(), q.occurrences());
    }

    private static AnalyticsVersionActivity toVersion(AnalyticsSnapshot.VersionActivity v) {
        return new AnalyticsVersionActivity(
                v.versionId(), v.name(),
                AnalyticsVersionActivity.StatusEnum.fromValue(v.status()),
                v.sessionCount());
    }

    private static AnalyticsLongestSession toLongest(AnalyticsSnapshot.LongestSession l) {
        return new AnalyticsLongestSession(
                l.sessionId(), l.anonymousId(),
                l.messageCount(), l.durationSeconds(), l.tokensUsed(),
                l.lastActivityAt().atOffset(ZoneOffset.UTC));
    }
}
