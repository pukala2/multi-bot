package com.multibot.bots.api;

import com.multibot.api.generated.api.BotsApi;
import com.multibot.api.generated.model.AddBotRequest;
import com.multibot.api.generated.model.AddBotResponse;
import com.multibot.api.generated.model.BotLimitResponse;
import com.multibot.auth.infrastructure.security.MultibotUserDetails;
import com.multibot.billing.domain.model.BotLimitDecision;
import com.multibot.bots.domain.port.in.AddBotUseCase.AddBotResult;
import com.multibot.bots.domain.port.in.ManageOwnerBotsUseCase;
import com.multibot.shared.exception.ForbiddenException;
import com.multibot.shared.organization.OrganizationId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-serve zarzadzanie botami przez ownera (contract-first, implementuje {@link BotsApi}).
 * Limit liczby botow per plan egzekwowany w serwisie (ConflictException -> 409).
 */
@RestController
class BotController implements BotsApi {

    private final ManageOwnerBotsUseCase ownerBots;

    BotController(ManageOwnerBotsUseCase ownerBots) {
        this.ownerBots = ownerBots;
    }

    @Override
    public ResponseEntity<BotLimitResponse> getBotLimit() {
        BotLimitDecision d = ownerBots.currentLimit(requireOrg());
        return ResponseEntity.ok(new BotLimitResponse(
                d.allowed(), d.currentBots(), d.maxBots(), d.unlimited()));
    }

    @Override
    public ResponseEntity<AddBotResponse> addOwnerBot(AddBotRequest addBotRequest) {
        AddBotResult r = ownerBots.addBot(requireOrg(), addBotRequest.getName(), addBotRequest.getUrl());
        return ResponseEntity.status(201).body(new AddBotResponse(
                r.botId().value(), r.botName(), r.widgetKey()));
    }

    private static OrganizationId requireOrg() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MultibotUserDetails me)) {
            throw new ForbiddenException("Brak uwierzytelnienia");
        }
        return me.organizationIdAsOptional()
                .orElseThrow(() -> new ForbiddenException("Konto nie należy do żadnej organizacji"));
    }
}
