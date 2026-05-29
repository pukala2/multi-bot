package com.multibot.bots.application;

import com.multibot.botversion.domain.model.ChatbotVersion;
import com.multibot.botversion.domain.port.in.ActivateVersionUseCase;
import com.multibot.botversion.domain.port.in.CreateVersionUseCase;
import com.multibot.bots.domain.port.in.AddBotUseCase;
import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.port.in.CreateTenantUseCase;
import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import com.multibot.widget.domain.port.in.UpdateWidgetSettingsUseCase;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orkiestracja provisioningu pojedynczego bota. Wersja i widget tworzone w kontekscie nowego
 * bota (TenantContext), bo te use case'y sa tenant-scoped.
 */
@Service
class BotProvisioningService implements AddBotUseCase {

    private final CreateTenantUseCase createTenant;
    private final CreateVersionUseCase createVersion;
    private final ActivateVersionUseCase activateVersion;
    private final GetWidgetConfigUseCase widgetConfig;
    private final UpdateWidgetSettingsUseCase widgetSettings;

    BotProvisioningService(CreateTenantUseCase createTenant,
                           CreateVersionUseCase createVersion,
                           ActivateVersionUseCase activateVersion,
                           GetWidgetConfigUseCase widgetConfig,
                           UpdateWidgetSettingsUseCase widgetSettings) {
        this.createTenant = createTenant;
        this.createVersion = createVersion;
        this.activateVersion = activateVersion;
        this.widgetConfig = widgetConfig;
        this.widgetSettings = widgetSettings;
    }

    @Override
    public AddBotResult addBot(OrganizationId organizationId, String botName, String siteUrl) {
        Tenant tenant = createTenant.create(organizationId, botName);
        TenantId tid = tenant.id();

        String widgetKey;
        TenantContext.set(tid);
        try {
            ChatbotVersion v = createVersion.create("Wersja 1", null);
            activateVersion.activate(v.id());
            WidgetConfig wc = widgetConfig.getForCurrentTenant();   // provisionuje settings + key
            widgetKey = wc.key();
            if (siteUrl != null && !siteUrl.isBlank()) {
                widgetSettings.updateAllowedOriginsForCurrentTenant(List.of(siteUrl));
            }
        } finally {
            TenantContext.clear();
        }

        return new AddBotResult(tid, tenant.name(), widgetKey);
    }
}
