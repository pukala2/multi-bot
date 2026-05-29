package com.multibot.bots.application;

import com.multibot.billing.domain.model.BotLimitDecision;
import com.multibot.billing.domain.port.in.CheckBotLimitUseCase;
import com.multibot.bots.domain.port.in.AddBotUseCase;
import com.multibot.bots.domain.port.in.AddBotUseCase.AddBotResult;
import com.multibot.bots.domain.port.in.ManageOwnerBotsUseCase;
import com.multibot.shared.exception.ConflictException;
import com.multibot.shared.organization.OrganizationId;
import com.multibot.tenant.domain.port.in.ListTenantsUseCase;
import org.springframework.stereotype.Service;

@Service
class OwnerBotService implements ManageOwnerBotsUseCase {

    private final ListTenantsUseCase listTenants;
    private final CheckBotLimitUseCase checkBotLimit;
    private final AddBotUseCase botProvisioning;

    OwnerBotService(ListTenantsUseCase listTenants,
                    CheckBotLimitUseCase checkBotLimit,
                    AddBotUseCase botProvisioning) {
        this.listTenants = listTenants;
        this.checkBotLimit = checkBotLimit;
        this.botProvisioning = botProvisioning;
    }

    @Override
    public BotLimitDecision currentLimit(OrganizationId organizationId) {
        int count = listTenants.listByOrganization(organizationId).size();
        return checkBotLimit.check(organizationId, count);
    }

    @Override
    public AddBotResult addBot(OrganizationId organizationId, String botName, String siteUrl) {
        BotLimitDecision decision = currentLimit(organizationId);
        if (!decision.allowed()) {
            throw new ConflictException(
                    "Osiągnięto limit stron w Twoim planie (" + decision.maxBots()
                            + "). Zmień plan, aby dodać więcej botów.");
        }
        return botProvisioning.addBot(organizationId, botName, siteUrl);
    }
}
