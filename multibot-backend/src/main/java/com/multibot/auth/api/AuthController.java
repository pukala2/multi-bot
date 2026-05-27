package com.multibot.auth.api;

import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.port.out.UserRepository;
import com.multibot.auth.infrastructure.security.MultibotUserDetails;
import com.multibot.shared.tenant.TenantId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final UserRepository userRepository;

    AuthController(AuthenticationManager authenticationManager,
                   UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
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

            return ResponseEntity.ok(toMeResponse(updated));
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
    ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MultibotUserDetails details)) {
            return ResponseEntity.status(401).build();
        }
        // Odswiezenie z DB zeby zobaczyc aktualny status (np. DISABLED w innym kanale)
        return userRepository.findById(details.userId())
                .map(this::toMeResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    private Map<String, Object> toMeResponse(User user) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", user.id().value().toString());
        body.put("email", user.email());
        body.put("role", user.role().name());
        body.put("status", user.status().name());
        body.put("tenantId", user.tenantId().map(TenantId::value).map(Object::toString).orElse(null));
        body.put("lastLoginAt", user.lastLoginAt().map(Instant::toString).orElse(null));
        return body;
    }

    record LoginRequest(
            @NotBlank String email,
            @NotBlank String password
    ) {
    }
}
