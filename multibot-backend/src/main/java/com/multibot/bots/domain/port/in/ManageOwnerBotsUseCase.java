package com.multibot.bots.domain.port.in;

import com.multibot.billing.domain.model.BotLimitDecision;
import com.multibot.bots.domain.port.in.AddBotUseCase.AddBotResult;
import com.multibot.shared.organization.OrganizationId;

public interface ManageOwnerBotsUseCase {

    /** Aktualny stan limitu botow dla organizacji (do wyswietlenia + bramki przycisku). */
    BotLimitDecision currentLimit(OrganizationId organizationId);

    /** Self-serve: owner dodaje bota w swojej organizacji. Egzekwuje limit (ConflictException gdy przekroczony). */
    AddBotResult addBot(OrganizationId organizationId, String botName);
}
