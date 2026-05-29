package com.multibot.billing.domain.port.in;

import com.multibot.billing.domain.model.BotLimitDecision;
import com.multibot.shared.organization.OrganizationId;

public interface CheckBotLimitUseCase {

    /**
     * Czy organizacja moze dodac kolejnego bota.
     * currentBotCount podaje wolajacy (modul tenant), bo billing nie zna botow.
     */
    BotLimitDecision check(OrganizationId organizationId, int currentBotCount);
}
