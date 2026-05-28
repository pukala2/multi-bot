package com.multibot.security.api;

import com.multibot.security.infrastructure.TurnstileVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publiczny endpoint dla widgeta — czy Turnstile jest wlaczony i jaki sitekey zaladowac.
 * Sciezka /api/widget/** zeby byla permitAll + CSRF-exempt.
 */
@RestController
class TurnstileController {

    private final TurnstileVerifier turnstile;

    TurnstileController(TurnstileVerifier turnstile) {
        this.turnstile = turnstile;
    }

    @GetMapping("/api/widget/turnstile")
    ResponseEntity<Map<String, Object>> turnstileConfig() {
        return ResponseEntity.ok(Map.of(
                "enabled", turnstile.isEnabled(),
                "siteKey", turnstile.siteKey() == null ? "" : turnstile.siteKey()
        ));
    }
}
