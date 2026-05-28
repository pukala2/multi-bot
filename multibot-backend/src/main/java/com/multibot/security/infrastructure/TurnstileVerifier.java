package com.multibot.security.infrastructure;

import com.multibot.security.config.SecurityShieldProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Weryfikacja tokenu Cloudflare Turnstile (anti-bot).
 * Feature flag multibot.security.turnstile.enabled — gdy false (dev), zawsze przepuszcza.
 * Na prod: enabled=true + secret-key z Cloudflare.
 */
@Service
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final boolean enabled;
    private final String secretKey;
    private final String siteKey;
    private final RestClient restClient;

    public TurnstileVerifier(SecurityShieldProperties props) {
        this.enabled = props.turnstile().enabled();
        this.secretKey = props.turnstile().secretKey();
        this.siteKey = props.turnstile().siteKey();
        this.restClient = RestClient.create();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String siteKey() {
        return siteKey;
    }

    /**
     * @return true jezeli token poprawny ALBO Turnstile wylaczony (dev).
     */
    @SuppressWarnings("unchecked")
    public boolean verify(String token, String remoteIp) {
        if (!enabled) return true;   // dev / nie skonfigurowany -> przepuszczamy
        if (token == null || token.isBlank()) return false;

        try {
            Map<String, Object> response = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body("secret=" + secretKey + "&response=" + token
                            + (remoteIp != null ? "&remoteip=" + remoteIp : ""))
                    .retrieve()
                    .body(Map.class);

            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            if (!success) {
                log.warn("Turnstile verification failed: {}", response);
            }
            return success;
        } catch (RuntimeException e) {
            log.error("Turnstile verify call failed", e);
            return false;   // fail closed: blad sieci = odrzuc
        }
    }
}
