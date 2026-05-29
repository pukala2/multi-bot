package com.multibot.auth.infrastructure.security;

import com.multibot.security.infrastructure.WidgetGuardFilter;
import com.multibot.tenancy.filter.TenantContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    SecurityConfig(JsonAuthEntryPoint jsonAuthEntryPoint) {
        this.jsonAuthEntryPoint = jsonAuthEntryPoint;
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
     * CORS dla CALEGO /api/**.
     *
     * Za reverse proxy (Caddy) backend widzi inny host niz Origin przegladarki, wiec Spring
     * traktuje KAZDY request z naglowkiem Origin (browser wysyla go na POST) jako cross-origin.
     * Bez configu dla danej sciezki Spring odrzuca "Invalid CORS request" (403) — to blokowalo
     * logowanie admina przez przegladarke.
     *
     * allowedOriginPatterns("*") + allowCredentials(true): Spring odbija faktyczny Origin
     * (nie literalna gwiazdke), wiec cookies admina dzialaja. Bezpieczenstwo nadal trzymaja:
     * CSRF token (X-XSRF-TOKEN, nieczytelny cross-origin), SameSite=Lax na cookie sesji,
     * oraz WidgetGuardFilter (per-key + origin whitelist) na endpointach widgeta.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            TenantContextFilter tenantContextFilter,
                                            WidgetGuardFilter widgetGuardFilter) throws Exception {
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
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // Guard PO autoryzacji (SecurityContext gotowy), PRZED TenantContextFilter
                // (guard wrzuca tenant w request attribute ktory czyta resolver).
                .addFilterAfter(widgetGuardFilter, AuthorizationFilter.class)
                .addFilterAfter(tenantContextFilter, WidgetGuardFilter.class)
                .build();
    }
}
