package com.multibot.superadmin.api;

import com.multibot.billing.domain.port.in.GetUsageUseCase;
import com.multibot.bots.domain.port.in.AddBotUseCase;
import com.multibot.bots.domain.port.in.AddBotUseCase.AddBotResult;
import com.multibot.organization.domain.model.Organization;
import com.multibot.organization.domain.port.in.ListOrganizationsUseCase;
import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.superadmin.domain.port.in.ProvisionOrganizationUseCase;
import com.multibot.superadmin.domain.port.in.ProvisionOrganizationUseCase.ProvisionCommand;
import com.multibot.superadmin.domain.port.in.ProvisionOrganizationUseCase.ProvisionResult;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.port.in.ListTenantsUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
class SuperAdminController {

    private final ListOrganizationsUseCase listOrganizations;
    private final ListTenantsUseCase listTenants;
    private final ProvisionOrganizationUseCase provisionOrganization;
    private final AddBotUseCase addBot;
    private final GetUsageUseCase getUsage;

    SuperAdminController(ListOrganizationsUseCase listOrganizations,
                         ListTenantsUseCase listTenants,
                         ProvisionOrganizationUseCase provisionOrganization,
                         AddBotUseCase addBot,
                         GetUsageUseCase getUsage) {
        this.listOrganizations = listOrganizations;
        this.listTenants = listTenants;
        this.provisionOrganization = provisionOrganization;
        this.addBot = addBot;
        this.getUsage = getUsage;
    }

    @GetMapping("/organizations")
    ResponseEntity<List<Map<String, Object>>> listAllOrganizations() {
        List<Map<String, Object>> result = listOrganizations.listAll().stream()
                .map(this::toOrganizationRow)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/organizations")
    ResponseEntity<Map<String, Object>> createOrganization(@Valid @RequestBody CreateOrganizationRequest req) {
        ProvisionResult r = provisionOrganization.provision(new ProvisionCommand(
                req.name(), req.ownerEmail(), req.plan(), req.companyName(), req.nip(),
                req.firstBotName(), req.firstBotUrl()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationId", r.organizationId().value().toString());
        body.put("organizationName", r.organizationName());
        body.put("ownerEmail", r.ownerEmail());
        body.put("generatedPassword", r.generatedPassword());   // pokazywane RAZ
        body.put("firstBotId", r.firstBotId().value().toString());
        body.put("firstBotName", r.firstBotName());
        body.put("widgetKey", r.widgetKey());
        return ResponseEntity.status(201).body(body);
    }

    @PostMapping("/organizations/{orgId}/bots")
    ResponseEntity<Map<String, Object>> addBotToOrganization(@PathVariable String orgId,
                                                             @Valid @RequestBody AddBotRequest req) {
        AddBotResult r = addBot.addBot(OrganizationId.of(orgId), req.name(), req.url());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("botId", r.botId().value().toString());
        body.put("botName", r.botName());
        body.put("widgetKey", r.widgetKey());
        return ResponseEntity.status(201).body(body);
    }

    private Map<String, Object> toOrganizationRow(Organization org) {
        List<Tenant> bots = listTenants.listByOrganization(org.id());

        long pooledUsed = 0;
        int effectiveLimit = -1;
        if (!bots.isEmpty()) {
            TenantId anyBot = bots.getFirst().id();
            try {
                pooledUsed = getUsage.getPooledUsedMessages(anyBot);
                effectiveLimit = getUsage.getEffectiveLimit(anyBot);
            } catch (RuntimeException ignored) {
                // organizacja bez planu w billing_plans — pokaz bez zuzycia
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", org.id().value().toString());
        row.put("name", org.name());
        row.put("slug", org.slug().value());
        row.put("plan", org.plan());
        row.put("status", org.status().name());
        row.put("companyName", org.companyName());
        row.put("nip", org.nip());
        row.put("userMessages", pooledUsed);
        row.put("effectiveLimit", effectiveLimit);
        row.put("bots", bots.stream().map(this::toBotRow).toList());
        row.put("createdAt", org.createdAt().toString());
        return row;
    }

    private Map<String, Object> toBotRow(Tenant bot) {
        long used = 0;
        try {
            used = getUsage.getCurrentUsage(bot.id()).userMessages();
        } catch (RuntimeException ignored) {
            // brak usage — pokaz 0
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", bot.id().value().toString());
        m.put("name", bot.name());
        m.put("slug", bot.slug().value());
        m.put("userMessages", used);
        return m;
    }

    record CreateOrganizationRequest(
            @NotBlank String name,
            @NotBlank @Email String ownerEmail,
            @NotBlank String plan,
            String companyName,
            String nip,
            String firstBotName,
            String firstBotUrl
    ) {
    }

    record AddBotRequest(
            @NotBlank String name,
            String url
    ) {
    }
}
