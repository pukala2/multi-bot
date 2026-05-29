package com.multibot.auth.infrastructure.security;

import com.multibot.security.infrastructure.WidgetGuardFilter;
import com.multibot.tenancy.filter.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    private final JsonAuthEntryPoint jsonAuthEntryPoint;
    private final List<String> panelOrigins;

    SecurityConfig(JsonAuthEntryPoint jsonAuthEntryPoint,
                   @Value("${multibot.cors.allowed-origins}") List<String> panelOrigins) {
        this.jsonAuthEntryPoint = jsonAuthEntryPoint;
        this.panelOrigins = panelOrigins;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Wylacza auto-rejestracje TenantContextFilter jako globalny servlet filter.
     * Filter jest dodawany do Spring Security chain w {@link #securityFilterChain} — musi byc
     * PO autoryzacji zeby SecurityContext byl juz ustawiony.
     */
    @Bean
    FilterRegistrationBean<TenantContextFilter> disableTenantContextFilterAutoRegistration(
            TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /**
     * Dwie polityki CORS:
     *  - Panel ("/api/**" z cookie sesji): TYLKO zaufane originy z konfiguracji
     *    (multibot.cors.allowed-origins). allowCredentials(true) wymaga jawnej listy — gwiazdka
     *    z credentials byłaby dziurą (dowolna strona czytałaby dane zalogowanego usera).
     *    Za reverse proxy (Caddy) backend widzi inny host niż Origin, więc nawet same-origin panel
     *    musi być na liście.
     *  - Widget + chat (wołane z domen klientów): originy "*", ale BEZ credentials — autoryzacja
     *    idzie kluczem widgetu (WidgetGuardFilter), nie cookie. Bardziej specyficzne wzorce muszą
     *    być zarejestrowane PRZED "/api/**".
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration panel = new CorsConfiguration();
        panel.setAllowedOrigins(panelOrigins);
        panel.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        panel.setAllowedHeaders(List.of("*"));
        panel.setAllowCredentials(true);
        panel.setMaxAge(3600L);

        CorsConfiguration widget = new CorsConfiguration();
        widget.setAllowedOriginPatterns(List.of("*"));
        widget.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        widget.setAllowedHeaders(List.of("*"));
        widget.setAllowCredentials(false);
        widget.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/widget/**", widget);
        source.registerCorsConfiguration("/api/chat/**", widget);
        source.registerCorsConfiguration("/api/**", panel);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            TenantContextFilter tenantContextFilter,
                                            WidgetGuardFilter widgetGuardFilter,
                                            LoginRateLimitFilter loginRateLimitFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieName("XSRF-TOKEN");
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        return http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfHandler)
                        // widget + chat (anonimowy widget, key-based) + actuator nie wymagaja CSRF.
                        // Admin test chat jest same-origin + SameSite cookie -> CSRF redundantny.
                        .ignoringRequestMatchers("/api/widget/**", "/api/chat/**", "/actuator/**"))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(h -> h
                        // Domyślne (nosniff, X-Frame-Options DENY) zostają; dokładamy HSTS, CSP, Referrer-Policy.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(r -> r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // API zwraca JSON (+ Swagger w dev). Restrykcyjnie, ale bez blokowania swagger-ui.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                + "script-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'")))
                .authorizeHttpRequests(a -> a
                        // Public
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers("/api/widget/**").permitAll()
                        // Widget chat (anonimowy) — authz przepuszcza, WidgetGuardFilter egzekwuje key+origin+limit.
                        // Admin test chat trafia tu tez ale jest authenticated -> guard go przepuszcza.
                        .requestMatchers(HttpMethod.POST, "/api/chat/sessions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chat/sessions/*/messages").permitAll()
                        // Super admin — tylko rola SUPER_ADMIN
                        .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")
                        // Self-serve zarządzanie botami — tylko OWNER (super admin używa /api/super-admin)
                        .requestMatchers("/api/bots/**").hasRole("OWNER")
                        // Reszta tylko zalogowani (lista sesji, historia, dokumenty, billing, ...)
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthEntryPoint))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)        // wlasny /api/auth/logout w controllerze
                // Throttling logowania PRZED CSRF/auth — chroni przed brute-force.
                .addFilterBefore(loginRateLimitFilter, CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // Guard PO autoryzacji (SecurityContext gotowy), PRZED TenantContextFilter
                // (guard wrzuca tenant w request attribute ktory czyta resolver).
                .addFilterAfter(widgetGuardFilter, AuthorizationFilter.class)
                .addFilterAfter(tenantContextFilter, WidgetGuardFilter.class)
                .build();
    }
}
