package com.multibot.auth.infrastructure.security;

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
 * Logika:
 *   1. Jesli w sesji jest atrybut "impersonatedTenantId" (Faza C) -> uzyj go.
 *   2. Jesli zalogowany user ma tenantId -> uzyj go.
 *   3. Inaczej (super admin bez impersonacji, widget anonimowy) -> brak tenanta.
 *      Endpointy widgetowe maja wlasne tenant resolution po API key (do dorobienia).
 *      Dla MVP widget korzysta z DEFAULT_TENANT przez fallback w MockTenantResolver
 *      ale gdy mode=auth ten fallback nie dziala — widget endpoint ma byc dorobiony osobno.
 */
@Component
@ConditionalOnProperty(name = "multibot.tenancy.mode", havingValue = "auth")
public class AuthenticatedTenantResolver implements TenantResolver {

    private static final String IMPERSONATION_ATTR = "impersonatedTenantId";

    /**
     * Tymczasowy fallback dla niezalogowanych /api/widget/** zeby istniejacy widget
     * dzialal bez auth dopoki nie dorobimy widget tenant resolution po API key.
     */
    private static final TenantId DEFAULT_TENANT_FALLBACK = TenantId.of(
            UUID.fromString("00000000-0000-7000-8000-000000000001"));

    @Override
    public Optional<TenantId> resolve(HttpServletRequest request) {
        // 1. impersonacja z sesji
        if (request.getSession(false) != null) {
            Object impersonated = request.getSession(false).getAttribute(IMPERSONATION_ATTR);
            if (impersonated instanceof UUID uuid) {
                return Optional.of(TenantId.of(uuid));
            }
        }

        // 2. tenant zalogowanego usera
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof MultibotUserDetails userDetails) {
            return userDetails.tenantIdAsOptional();
        }

        // 3. widget anonim — fallback do default tenant (na razie)
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/widget/")) {
            return Optional.of(DEFAULT_TENANT_FALLBACK);
        }

        return Optional.empty();
    }
}
