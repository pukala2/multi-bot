package com.multibot.superadmin.api;

import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.billing.domain.port.in.GetUsageUseCase;
import com.multibot.superadmin.domain.port.in.ProvisionTenantUseCase;
import com.multibot.superadmin.domain.port.in.ProvisionTenantUseCase.ProvisionCommand;
import com.multibot.superadmin.domain.port.in.ProvisionTenantUseCase.ProvisionResult;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.port.in.ListTenantsUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
class SuperAdminController {

    private final ListTenantsUseCase listTenants;
    private final ProvisionTenantUseCase provisionTenant;
    private final GetUsageUseCase getUsage;

    SuperAdminController(ListTenantsUseCase listTenants,
                         ProvisionTenantUseCase provisionTenant,
                         GetUsageUseCase getUsage) {
        this.listTenants = listTenants;
        this.provisionTenant = provisionTenant;
        this.getUsage = getUsage;
    }

    @GetMapping("/tenants")
    ResponseEntity<List<Map<String, Object>>> listAllTenants() {
        List<Map<String, Object>> result = listTenants.listAll().stream()
                .map(this::toTenantRow)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tenants")
    ResponseEntity<Map<String, Object>> createTenant(@Valid @RequestBody CreateTenantRequest req) {
        ProvisionResult r = provisionTenant.provision(new ProvisionCommand(
                req.name(), req.ownerEmail(), req.plan(), req.companyName(), req.nip()));

        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", r.tenantId().value().toString());
        body.put("tenantName", r.tenantName());
        body.put("ownerEmail", r.ownerEmail());
        body.put("generatedPassword", r.generatedPassword());   // pokazywane RAZ
        body.put("widgetKey", r.widgetKey());
        return ResponseEntity.status(201).body(body);
    }

    private Map<String, Object> toTenantRow(Tenant t) {
        long used = 0;
        int limit = -1;
        try {
            TenantUsage usage = getUsage.getCurrentUsage(t.id());
            used = usage.userMessages();
            limit = getUsage.getEffectiveLimit(t.id());
        } catch (RuntimeException ignored) {
            // tenant bez planu w billing_plans — pokaz bez zuzycia
        }
        Map<String, Object> row = new HashMap<>();
        row.put("id", t.id().value().toString());
        row.put("name", t.name());
        row.put("slug", t.slug().value());
        row.put("plan", t.plan().name());
        row.put("status", t.status().name());
        row.put("companyName", t.companyName());
        row.put("nip", t.nip());
        row.put("userMessages", used);
        row.put("effectiveLimit", limit);
        row.put("createdAt", t.createdAt().toString());
        return row;
    }

    record CreateTenantRequest(
            @NotBlank String name,
            @NotBlank @Email String ownerEmail,
            @NotBlank String plan,
            String companyName,
            String nip
    ) {
    }
}
