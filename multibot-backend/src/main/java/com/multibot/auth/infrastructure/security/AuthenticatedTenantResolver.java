package com.multibot.auth.infrastructure.security;

import com.multibot.security.infrastructure.WidgetGuardFilter;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.resolver.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * TenantResolver wlaczany gdy multibot.tenancy.mode=auth.
 * Kolejnosc:
 *   1. impersonacja z sesji (atrybut "impersonatedTenantId") — Faza D.
 *   2. tenant zalogowanego usera (admin) z SecurityContext.
 *   3. tenant widgeta wrzucony przez WidgetGuardFilter w request attribute
 *      (anonimowy widget, juz po walidacji key + origin + rate limit).
 *   4. brak tenanta -> endpoint poleci na TenantContext.get() z bledem (zamierzone).
 */
@Component
@ConditionalOnProperty(name = "multibot.tenancy.mode", havingValue = "auth")
public class AuthenticatedTenantResolver implements TenantResolver {

    private static final String IMPERSONATION_ATTR = "impersonatedTenantId";

    @Override
    public Optional<TenantId> resolve(HttpServletRequest request) {
        // 1. impersonacja z sesji
        if (request.getSession(false) != null) {
            Object impersonated = request.getSession(false).getAttribute(IMPERSONATION_ATTR);
            if (impersonated instanceof UUID uuid) {
                return Optional.of(TenantId.of(uuid));
            }
        }

        // 2. tenant zalogowanego usera (admin)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof MultibotUserDetails userDetails) {
            return userDetails.tenantIdAsOptional();
        }

        // 3. tenant widgeta (anonim) — ustawiony przez WidgetGuardFilter po walidacji
        Object widgetTenant = request.getAttribute(WidgetGuardFilter.TENANT_ATTRIBUTE);
        if (widgetTenant instanceof UUID uuid) {
            return Optional.of(TenantId.of(uuid));
        }

        return Optional.empty();
    }
}
