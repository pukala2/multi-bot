package com.multibot.bots.domain.port.in;

import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;

public interface AddBotUseCase {

    /**
     * Provisionuje bota w organizacji: tenant + wersja v1 (aktywna) + widget. BEZ sprawdzania limitu.
     * siteUrl (nullable) — adres strony bota; ustawia allowed_origins na kluczu widgeta.
     */
    AddBotResult addBot(OrganizationId organizationId, String botName, String siteUrl);

    record AddBotResult(
            TenantId botId,
            String botName,
            String widgetKey
    ) {
    }
}
