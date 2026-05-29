package com.multibot.auth.infrastructure.security;

import com.multibot.security.infrastructure.WidgetGuardFilter;
import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.resolver.TenantResolver;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.port.in.ListTenantsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TenantResolver wlaczany gdy multibot.tenancy.mode=auth. Zwraca AKTYWNEGO bota (tenant).
 * Kolejnosc:
 *   1. impersonacja z sesji ("impersonatedTenantId") — super admin "wejdz jako klient" (Faza D).
 *   2. aktywny bot wybrany przez zalogowanego usera ("activeBotTenantId") — ustawiany przy
 *      loginie / przelaczniku botow (juz po walidacji ze bot nalezy do organizacji usera).
 *   3. fallback: zalogowany user z organizacja ale bez wybranego bota (np. sesja sprzed deployu)
 *      -> auto-wybor pierwszego bota i zapis w sesji.
 *   4. tenant widgeta wrzucony przez WidgetGuardFilter (anonim, po walidacji key+origin+limit).
 *   5. brak -> TenantContext.get() poleci bledem (zamierzone).
 */
@Component
@ConditionalOnProperty(name = "multibot.tenancy.mode", havingValue = "auth")
public class AuthenticatedTenantResolver implements TenantResolver {

    public static final String ACTIVE_BOT_ATTR = "activeBotTenantId";
    private static final String IMPERSONATION_ATTR = "impersonatedTenantId";

    private final ListTenantsUseCase listTenants;

    AuthenticatedTenantResolver(ListTenantsUseCase listTenants) {
        this.listTenants = listTenants;
    }

    @Override
    public Optional<TenantId> resolve(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object impersonated = session.getAttribute(IMPERSONATION_ATTR);
            if (impersonated instanceof UUID uuid) {
                return Optional.of(TenantId.of(uuid));
            }
            Object activeBot = session.getAttribute(ACTIVE_BOT_ATTR);
            if (activeBot instanceof UUID uuid) {
                return Optional.of(TenantId.of(uuid));
            }
        }

        Optional<TenantId> autoSelected = autoSelectForAuthenticatedUser(request);
        if (autoSelected.isPresent()) {
            return autoSelected;
        }

        Object widgetTenant = request.getAttribute(WidgetGuardFilter.TENANT_ATTRIBUTE);
        if (widgetTenant instanceof UUID uuid) {
            return Optional.of(TenantId.of(uuid));
        }

        return Optional.empty();
    }

    private Optional<TenantId> autoSelectForAuthenticatedUser(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof MultibotUserDetails userDetails)) {
            return Optional.empty();
        }
        Optional<OrganizationId> orgId = userDetails.organizationIdAsOptional();
        if (orgId.isEmpty()) {
            return Optional.empty();   // SUPER_ADMIN — bez wlasnych botow
        }
        List<Tenant> bots = listTenants.listByOrganization(orgId.get());
        if (bots.isEmpty()) {
            return Optional.empty();
        }
        TenantId first = bots.getFirst().id();
        request.getSession(true).setAttribute(ACTIVE_BOT_ATTR, first.value());
        return Optional.of(first);
    }
}
