package com.multibot.auth.infrastructure.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttling logowania (anti-brute-force) na POST /api/auth/login — per IP klienta.
 * In-memory (Bucket4j), single-instance MVP; przy skali -> backend Redis.
 * Limit konfigurowalny: multibot.security.login.max-per-minute (domyślnie 10).
 * Przekroczenie -> 429 ProblemDetail (RFC 7807), bez dotykania AuthenticationManagera.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final int maxPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(@Value("${multibot.security.login.max-per-minute:10}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isLoginAttempt(request)) {
            chain.doFilter(request, response);
            return;
        }
        Bucket bucket = buckets.computeIfAbsent(clientIp(request), k -> newBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }
        writeTooManyRequests(response);
    }

    private boolean isLoginAttempt(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(maxPerMinute)
                .refillGreedy(maxPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /** Realny IP zza reverse proxy (Caddy ustawia X-Forwarded-For); fallback na remote addr. */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"type":"https://multibot.app/errors/too-many-login-attempts",\
                "title":"Too Many Requests","status":429,\
                "detail":"Zbyt wiele prób logowania. Spróbuj ponownie za chwilę.",\
                "code":"TOO_MANY_LOGIN_ATTEMPTS"}""");
    }
}
