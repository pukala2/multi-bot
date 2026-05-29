package com.multibot.superadmin.api;

import com.multibot.api.generated.api.SuperAdminApi;
import com.multibot.api.generated.model.AddBotRequest;
import com.multibot.api.generated.model.AddBotResponse;
import com.multibot.api.generated.model.CreateOrganizationRequest;
import com.multibot.api.generated.model.OrganizationBotRow;
import com.multibot.api.generated.model.OrganizationRow;
import com.multibot.api.generated.model.ProvisionResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Provisioning klientow przez SUPER_ADMINa (contract-first, implementuje {@link SuperAdminApi}).
 * Dodawanie botow tutaj omija limit planu.
 */
@RestController
class SuperAdminController implements SuperAdminApi {

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

    @Override
    public ResponseEntity<List<OrganizationRow>> listOrganizations() {
        List<OrganizationRow> result = listOrganizations.listAll().stream()
                .map(this::toOrganizationRow)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<ProvisionResponse> createOrganization(CreateOrganizationRequest req) {
        ProvisionResult r = provisionOrganization.provision(new ProvisionCommand(
                req.getName(), req.getOwnerEmail(), req.getPlan(), req.getCompanyName(), req.getNip(),
                req.getFirstBotName(), req.getFirstBotUrl()));

        ProvisionResponse body = new ProvisionResponse(
                r.organizationId().value(),
                r.organizationName(),
                r.ownerEmail(),
                r.generatedPassword(),     // pokazywane RAZ
                r.firstBotId().value(),
                r.firstBotName(),
                r.widgetKey());
        return ResponseEntity.status(201).body(body);
    }

    @Override
    public ResponseEntity<AddBotResponse> addBotToOrganization(UUID orgId, AddBotRequest req) {
        AddBotResult r = addBot.addBot(OrganizationId.of(orgId), req.getName(), req.getUrl());
        return ResponseEntity.status(201).body(new AddBotResponse(
                r.botId().value(), r.botName(), r.widgetKey()));
    }

    private OrganizationRow toOrganizationRow(Organization org) {
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

        OrganizationRow row = new OrganizationRow(
                org.id().value(),
                org.name(),
                org.slug().value(),
                org.plan(),
                org.status().name(),
                pooledUsed,
                effectiveLimit,
                bots.stream().map(this::toBotRow).toList(),
                org.createdAt().toString());
        row.setCompanyName(org.companyName());
        row.setNip(org.nip());
        return row;
    }

    private OrganizationBotRow toBotRow(Tenant bot) {
        long used = 0;
        try {
            used = getUsage.getCurrentUsage(bot.id()).userMessages();
        } catch (RuntimeException ignored) {
            // brak usage — pokaz 0
        }
        return new OrganizationBotRow(bot.id().value(), bot.name(), bot.slug().value(), used);
    }
}
