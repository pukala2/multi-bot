package com.multibot.analytics.domain.port.in;

import com.multibot.analytics.domain.model.AnalyticsSnapshot;

public interface GetAnalyticsUseCase {

    AnalyticsSnapshot get(AnalyticsPeriod period);

    enum AnalyticsPeriod {
        D1, D7, D30, D90;

        public int days() {
            return switch (this) {
                case D1 -> 1;
                case D7 -> 7;
                case D30 -> 30;
                case D90 -> 90;
            };
        }
    }
}
