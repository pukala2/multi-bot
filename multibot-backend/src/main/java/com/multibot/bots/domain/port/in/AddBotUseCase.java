package com.multibot.bots.domain.port.in;

import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;

public interface AddBotUseCase {

    /** Provisionuje bota w organizacji: tenant + wersja v1 (aktywna) + widget. BEZ sprawdzania limitu. */
    AddBotResult addBot(OrganizationId organizationId, String botName);

    record AddBotResult(
            TenantId botId,
            String botName,
            String widgetKey
    ) {
    }
}
