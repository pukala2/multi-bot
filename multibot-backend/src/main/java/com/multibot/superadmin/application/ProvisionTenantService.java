package com.multibot.superadmin.application;

import com.multibot.auth.domain.port.in.CreateUserUseCase;
import com.multibot.botversion.domain.model.ChatbotVersion;
import com.multibot.botversion.domain.port.in.ActivateVersionUseCase;
import com.multibot.botversion.domain.port.in.CreateVersionUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.superadmin.domain.port.in.ProvisionTenantUseCase;
import com.multibot.tenancy.TenantContext;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantPlan;
import com.multibot.tenant.domain.port.in.CreateTenantUseCase;
import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Orkiestracja pelnego provisioningu klienta (to co wczesniej robilem recznie SQL-em):
 *   tenant + owner (auto-haslo) + wersja v1 (aktywna) + widget + klucz.
 *
 * Wersja i widget tworzone w kontekscie nowego tenanta (TenantContext), bo te use case'y
 * sa tenant-scoped. Nie owijamy calosci w jedna transakcje — kazdy use case ma wlasna.
 */
@Service
class ProvisionTenantService implements ProvisionTenantUseCase {

    private static final SecureRandom RND = new SecureRandom();

    private final CreateTenantUseCase createTenant;
    private final CreateUserUseCase createUser;
    private final CreateVersionUseCase createVersion;
    private final ActivateVersionUseCase activateVersion;
    private final GetWidgetConfigUseCase widgetConfig;

    ProvisionTenantService(CreateTenantUseCase createTenant,
                           CreateUserUseCase createUser,
                           CreateVersionUseCase createVersion,
                           ActivateVersionUseCase activateVersion,
                           GetWidgetConfigUseCase widgetConfig) {
        this.createTenant = createTenant;
        this.createUser = createUser;
        this.createVersion = createVersion;
        this.activateVersion = activateVersion;
        this.widgetConfig = widgetConfig;
    }

    @Override
    public ProvisionResult provision(ProvisionCommand cmd) {
        TenantPlan plan = parsePlan(cmd.plan());

        // 1. tenant
        Tenant tenant = createTenant.create(cmd.name(), plan, cmd.companyName(), cmd.nip());
        TenantId tid = tenant.id();

        // 2. owner z wygenerowanym haslem
        String rawPassword = generatePassword();
        createUser.createOwner(tid, cmd.ownerEmail(), rawPassword);

        // 3. wersja v1 + widget — w kontekscie nowego tenanta
        String widgetKey;
        TenantContext.set(tid);
        try {
            ChatbotVersion v = createVersion.create("Wersja 1", null);
            activateVersion.activate(v.id());
            WidgetConfig wc = widgetConfig.getForCurrentTenant();   // provisionuje settings + key
            widgetKey = wc.key();
        } finally {
            TenantContext.clear();
        }

        return new ProvisionResult(tid, tenant.name(), cmd.ownerEmail(), rawPassword, widgetKey);
    }

    private static TenantPlan parsePlan(String plan) {
        try {
            return TenantPlan.valueOf(plan == null ? "FREE" : plan.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TenantPlan.FREE;
        }
    }

    /** Mocne haslo: ~12 znakow base64url + sufiks zapewniajacy klasy znakow. */
    private static String generatePassword() {
        byte[] b = new byte[9];
        RND.nextBytes(b);
        String core = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
                .replaceAll("[-_]", "");
        return core + "Aa1!";
    }
}
