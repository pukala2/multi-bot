package com.multibot.security.infrastructure;

import com.multibot.shared.tenant.TenantId;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ochrona endpointow widgetowych (anonimowe rozmowy):
 *   POST /api/chat/sessions              (start sesji)
 *   POST /api/chat/sessions/{id}/messages (wiadomosc)
 *
 * Logika:
 *   - Zalogowany admin (cookie sesyjny) -> przepusc, tenant z SecurityContext (resolver).
 *   - Anonim (widget) -> wymagaj X-Multibot-Widget-Key + dozwolony Origin (403 jak zle).
 *     Rate limit per IP i per sesja (429). Turnstile na start sesji (jak enabled).
 *     Po sukcesie: tenant z klucza wrzucony w request attribute dla resolvera.
 *
 * Atrybut request "multibot.widget.tenantId" jest czytany przez AuthenticatedTenantResolver.
 */
@Component
public class WidgetGuardFilter extends OncePerRequestFilter {

    public static final String TENANT_ATTRIBUTE = "multibot.widget.tenantId";

    private static final Logger log = LoggerFactory.getLogger(WidgetGuardFilter.class);
    private static final Pattern MESSAGES_PATH =
            Pattern.compile("^/api/chat/sessions/([^/]+)/messages$");
    private static final String SESSIONS_PATH = "/api/chat/sessions";

    private final GetWidgetConfigUseCase widgetConfig;
    private final RateLimiterService rateLimiter;
    private final TurnstileVerifier turnstile;

    public WidgetGuardFilter(GetWidgetConfigUseCase widgetConfig,
                             RateLimiterService rateLimiter,
                             TurnstileVerifier turnstile) {
        this.widgetConfig = widgetConfig;
        this.rateLimiter = rateLimiter;
        this.turnstile = turnstile;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isPost = "POST".equalsIgnoreCase(request.getMethod());

        boolean isSessionStart = isPost && SESSIONS_PATH.equals(path);
        var messagesMatcher = MESSAGES_PATH.matcher(path);
        boolean isMessage = isPost && messagesMatcher.matches();

        if (!isSessionStart && !isMessage) {
            chain.doFilter(request, response);
            return;
        }

        // Admin zalogowany -> przepusc bez sprawdzania widget key
        if (isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        // --- Anonim (widget) ---
        String key = request.getHeader("X-Multibot-Widget-Key");
        String origin = request.getHeader("Origin");

        Optional<TenantId> tenant = widgetConfig.validateAndResolveTenant(key, origin);
        if (tenant.isEmpty()) {
            reject(response, HttpStatus.FORBIDDEN, "INVALID_WIDGET_KEY",
                    "Niepoprawny klucz widgeta lub niedozwolona domena");
            return;
        }

        String ip = clientIp(request);
        if (!rateLimiter.tryConsumeForIp(ip)) {
            reject(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_IP",
                    "Zbyt wiele zapytan z tego adresu. Sprobuj pozniej.");
            return;
        }

        if (isMessage) {
            String sessionId = messagesMatcher.group(1);
            if (!rateLimiter.tryConsumeForSession(sessionId)) {
                reject(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_SESSION",
                        "Zbyt wiele wiadomosci w tej rozmowie. Sprobuj pozniej.");
                return;
            }
        }

        if (isSessionStart && turnstile.isEnabled()) {
            String tsToken = request.getHeader("X-Turnstile-Token");
            if (!turnstile.verify(tsToken, ip)) {
                reject(response, HttpStatus.FORBIDDEN, "TURNSTILE_FAILED",
                        "Weryfikacja anty-bot nieudana");
                return;
            }
        }

        request.setAttribute(TENANT_ATTRIBUTE, tenant.get().value());
        chain.doFilter(request, response);
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        log.warn("Widget guard reject: {} - {}", code, message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
