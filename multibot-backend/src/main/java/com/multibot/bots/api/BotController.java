package com.multibot.bots.api;

import com.multibot.auth.infrastructure.security.MultibotUserDetails;
import com.multibot.billing.domain.model.BotLimitDecision;
import com.multibot.bots.domain.port.in.AddBotUseCase.AddBotResult;
import com.multibot.bots.domain.port.in.ManageOwnerBotsUseCase;
import com.multibot.shared.exception.ForbiddenException;
import com.multibot.shared.organization.OrganizationId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-serve zarzadzanie botami przez ownera. Limit liczby botow per plan egzekwowany w serwisie.
 */
@RestController
@RequestMapping("/api/bots")
class BotController {

    private final ManageOwnerBotsUseCase ownerBots;

    BotController(ManageOwnerBotsUseCase ownerBots) {
        this.ownerBots = ownerBots;
    }

    @GetMapping("/limit")
    ResponseEntity<Map<String, Object>> limit(@AuthenticationPrincipal MultibotUserDetails me) {
        OrganizationId org = requireOrg(me);
        BotLimitDecision d = ownerBots.currentLimit(org);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("allowed", d.allowed());
        body.put("currentBots", d.currentBots());
        body.put("maxBots", d.maxBots());      // -1 = bez limitu
        body.put("unlimited", d.unlimited());
        return ResponseEntity.ok(body);
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> addBot(@AuthenticationPrincipal MultibotUserDetails me,
                                               @Valid @RequestBody AddBotRequest req) {
        OrganizationId org = requireOrg(me);
        AddBotResult r = ownerBots.addBot(org, req.name(), req.url());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("botId", r.botId().value().toString());
        body.put("botName", r.botName());
        body.put("widgetKey", r.widgetKey());
        return ResponseEntity.status(201).body(body);
    }

    private static OrganizationId requireOrg(MultibotUserDetails me) {
        if (me == null) {
            throw new ForbiddenException("Brak uwierzytelnienia");
        }
        return me.organizationIdAsOptional()
                .orElseThrow(() -> new ForbiddenException("Konto nie należy do żadnej organizacji"));
    }

    record AddBotRequest(
            @NotBlank String name,
            String url
    ) {
    }
}
