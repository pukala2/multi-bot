package com.multibot.auth.api;

import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.port.out.UserRepository;
import com.multibot.auth.infrastructure.security.AuthenticatedTenantResolver;
import com.multibot.auth.infrastructure.security.MultibotUserDetails;
import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.port.in.GetTenantUseCase;
import com.multibot.tenant.domain.port.in.ListTenantsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final UserRepository userRepository;
    private final ListTenantsUseCase listTenants;
    private final GetTenantUseCase getTenant;

    AuthController(AuthenticationManager authenticationManager,
                   UserRepository userRepository,
                   ListTenantsUseCase listTenants,
                   GetTenantUseCase getTenant) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.listTenants = listTenants;
        this.getTenant = getTenant;
    }

    @PostMapping("/login")
    ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        try {
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    request.email(), request.password());
            Authentication authentication = authenticationManager.authenticate(authToken);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            MultibotUserDetails details = (MultibotUserDetails) authentication.getPrincipal();
            User user = userRepository.findById(details.userId())
                    .orElseThrow(() -> new IllegalStateException("User disappeared mid-login"));
            User updated = user.withLastLoginAt(Instant.now());
            userRepository.save(updated);

            autoSelectActiveBot(updated, httpRequest);

            return ResponseEntity.ok(toMeResponse(updated, httpRequest));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "BAD_CREDENTIALS",
                    "message", "Niepoprawny email lub hasło"
            ));
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    ResponseEntity<Map<String, Object>> me(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MultibotUserDetails details)) {
            return ResponseEntity.status(401).build();
        }
        // Odswiezenie z DB zeby zobaczyc aktualny status (np. DISABLED w innym kanale)
        return userRepository.findById(details.userId())
                .map(user -> toMeResponse(user, request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    /** Przelaczenie aktywnego bota. Waliduje ze bot nalezy do organizacji zalogowanego usera. */
    @PostMapping("/active-bot")
    ResponseEntity<Map<String, Object>> switchActiveBot(@Valid @RequestBody ActiveBotRequest req,
                                                        Authentication authentication,
                                                        HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MultibotUserDetails details)) {
            return ResponseEntity.status(401).build();
        }
        OrganizationId orgId = details.organizationIdAsOptional().orElse(null);
        if (orgId == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "NO_ORGANIZATION", "message", "Super admin nie ma własnych botów"));
        }
        TenantId botId;
        try {
            botId = TenantId.of(req.tenantId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_BOT_ID", "message", "Niepoprawny identyfikator bota"));
        }
        Tenant bot = getTenant.getById(botId);   // 404 gdy nie istnieje (NotFoundException)
        if (!bot.organizationId().equals(orgId)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "BOT_NOT_IN_ORG", "message", "Bot nie należy do Twojej organizacji"));
        }
        request.getSession(true).setAttribute(AuthenticatedTenantResolver.ACTIVE_BOT_ATTR, bot.id().value());
        return ResponseEntity.ok(toBotMap(bot));
    }

    private void autoSelectActiveBot(User user, HttpServletRequest request) {
        if (user.organizationId() == null) return;
        List<Tenant> bots = listTenants.listByOrganization(user.organizationId());
        if (bots.isEmpty()) return;
        request.getSession(true)
                .setAttribute(AuthenticatedTenantResolver.ACTIVE_BOT_ATTR, bots.getFirst().id().value());
    }

    private Map<String, Object> toMeResponse(User user, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", user.id().value().toString());
        body.put("email", user.email());
        body.put("role", user.role().name());
        body.put("status", user.status().name());
        body.put("organizationId", user.organizationId() == null ? null : user.organizationId().value().toString());
        body.put("lastLoginAt", user.lastLoginAt() == null ? null : user.lastLoginAt().toString());

        List<Tenant> bots = user.organizationId() == null
                ? List.of()
                : listTenants.listByOrganization(user.organizationId());
        body.put("bots", bots.stream().map(AuthController::toBotMap).toList());

        UUID activeId = activeBotId(request);
        Tenant active = bots.stream()
                .filter(b -> b.id().value().equals(activeId))
                .findFirst()
                .orElse(bots.isEmpty() ? null : bots.getFirst());
        body.put("activeBot", active == null ? null : toBotMap(active));
        return body;
    }

    private static UUID activeBotId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object attr = session.getAttribute(AuthenticatedTenantResolver.ACTIVE_BOT_ATTR);
        return attr instanceof UUID uuid ? uuid : null;
    }

    private static Map<String, Object> toBotMap(Tenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id().value().toString());
        m.put("name", t.name());
        m.put("slug", t.slug().value());
        return m;
    }

    record LoginRequest(
            @NotBlank String email,
            @NotBlank String password
    ) {
    }

    record ActiveBotRequest(
            @NotBlank String tenantId
    ) {
    }
}
