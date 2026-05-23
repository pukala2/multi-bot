package com.multibot.admin.domain.port.in;

import com.multibot.admin.domain.model.AdminStats;

public interface GetAdminStatsUseCase {

    AdminStats getStats();
}
