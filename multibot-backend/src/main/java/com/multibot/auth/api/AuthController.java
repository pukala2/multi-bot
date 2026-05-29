package com.multibot.auth.api;

import com.multibot.api.generated.api.AuthApi;
import com.multibot.api.generated.model.ActiveBotRequest;
import com.multibot.api.generated.model.BotSummary;
import com.multibot.api.generated.model.LoginRequest;
import com.multibot.api.generated.model.MeResponse;
import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.port.out.UserRepository;
import com.multibot.auth.infrastructure.security.AuthenticatedTenantResolver;
import com.multibot.auth.infrastructure.security.MultibotUserDetails;
import com.multibot.shared.exception.ForbiddenException;
import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.port.in.GetTenantUseCase;
import com.multibot.tenant.domain.port.in.ListTenantsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Auth contract-first (implementuje wygenerowany {@link AuthApi}).
 * Logika sesji/cookie: kontekst zapisujemy do atrybutu sesji (równoważne SecurityContextRepository.saveContext),
 * dzięki czemu nie potrzebujemy HttpServletResponse (interfejs go nie przekazuje).
 * HttpServletRequest wstrzykiwany jako request-scoped proxy.
 */
@RestController
class AuthController implements AuthApi {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final ListTenantsUseCase listTenants;
    private final GetTenantUseCase getTenant;
    private final HttpServletRequest httpRequest;

    AuthController(AuthenticationManager authenticationManager,
                   UserRepository userRepository,
                   ListTenantsUseCase listTenants,
                   GetTenantUseCase getTenant,
                   HttpServletRequest httpRequest) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.listTenants = listTenants;
        this.getTenant = getTenant;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<MeResponse> login(LoginRequest loginRequest) {
        // BadCredentialsException -> 401 (GlobalExceptionHandler)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        // Ochrona przed session fixation: rotacja id istniejącej sesji (np. z prime'a CSRF) po auth.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        MultibotUserDetails details = (MultibotUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(details.userId())
                .orElseThrow(() -> new IllegalStateException("User disappeared mid-login"));
        User updated = user.withLastLoginAt(Instant.now());
        userRepository.save(updated);

        autoSelectActiveBot(updated);

        return ResponseEntity.ok(toMeResponse(updated));
    }

    @Override
    public ResponseEntity<Void> logout() {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MeResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MultibotUserDetails details)) {
            return ResponseEntity.status(401).build();
        }
        // Odswiezenie z DB zeby zobaczyc aktualny status (np. DISABLED w innym kanale)
        return userRepository.findById(details.userId())
                .map(this::toMeResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** Przelaczenie aktywnego bota. Waliduje ze bot nalezy do organizacji zalogowanego usera. */
    @Override
    public ResponseEntity<BotSummary> switchActiveBot(ActiveBotRequest activeBotRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MultibotUserDetails details)) {
            return ResponseEntity.status(401).build();
        }
        OrganizationId orgId = details.organizationIdAsOptional()
                .orElseThrow(() -> new ForbiddenException("Super admin nie ma własnych botów"));

        TenantId botId = TenantId.of(activeBotRequest.getTenantId());
        Tenant bot = getTenant.getById(botId);   // 404 gdy nie istnieje (NotFoundException)
        if (!bot.organizationId().equals(orgId)) {
            throw new ForbiddenException("Bot nie należy do Twojej organizacji");
        }
        httpRequest.getSession(true).setAttribute(AuthenticatedTenantResolver.ACTIVE_BOT_ATTR, bot.id().value());
        return ResponseEntity.ok(toBotSummary(bot));
    }

    private void autoSelectActiveBot(User user) {
        if (user.organizationId() == null) return;
        List<Tenant> bots = listTenants.listByOrganization(user.organizationId());
        if (bots.isEmpty()) return;
        httpRequest.getSession(true)
                .setAttribute(AuthenticatedTenantResolver.ACTIVE_BOT_ATTR, bots.getFirst().id().value());
    }

    private MeResponse toMeResponse(User user) {
        List<Tenant> bots = user.organizationId() == null
                ? List.of()
                : listTenants.listByOrganization(user.organizationId());

        MeResponse response = new MeResponse(
                user.id().value(),
                user.email(),
                MeResponse.RoleEnum.fromValue(user.role().name()),
                MeResponse.StatusEnum.fromValue(user.status().name()),
                bots.stream().map(AuthController::toBotSummary).toList());
        response.setOrganizationId(user.organizationId() == null ? null : user.organizationId().value());
        response.setLastLoginAt(user.lastLoginAt() == null ? null : user.lastLoginAt().toString());

        UUID activeId = activeBotId();
        Tenant active = bots.stream()
                .filter(b -> b.id().value().equals(activeId))
                .findFirst()
                .orElse(bots.isEmpty() ? null : bots.getFirst());
        response.setActiveBot(active == null ? null : toBotSummary(active));
        return response;
    }

    private UUID activeBotId() {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) return null;
        Object attr = session.getAttribute(AuthenticatedTenantResolver.ACTIVE_BOT_ATTR);
        return attr instanceof UUID uuid ? uuid : null;
    }

    private static BotSummary toBotSummary(Tenant t) {
        return new BotSummary(t.id().value(), t.name(), t.slug().value());
    }
}
