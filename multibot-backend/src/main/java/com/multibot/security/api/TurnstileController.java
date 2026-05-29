package com.multibot.security.api;

import com.multibot.api.generated.api.TurnstileApi;
import com.multibot.api.generated.model.TurnstileConfigResponse;
import com.multibot.security.infrastructure.TurnstileVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publiczny endpoint dla widgeta — czy Turnstile jest wlaczony i jaki sitekey zaladowac.
 * Contract-first (implementuje {@link TurnstileApi}). Sciezka /api/widget/** jest permitAll + CSRF-exempt.
 */
@RestController
class TurnstileController implements TurnstileApi {

    private final TurnstileVerifier turnstile;

    TurnstileController(TurnstileVerifier turnstile) {
        this.turnstile = turnstile;
    }

    @Override
    public ResponseEntity<TurnstileConfigResponse> getTurnstileConfig() {
        return ResponseEntity.ok(new TurnstileConfigResponse(
                turnstile.isEnabled(),
                turnstile.siteKey() == null ? "" : turnstile.siteKey()));
    }
}
