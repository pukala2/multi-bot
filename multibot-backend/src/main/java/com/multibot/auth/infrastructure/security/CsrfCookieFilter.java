package com.multibot.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Wymusza eager CSRF token resolution -> XSRF-TOKEN cookie jest ustawiany na kazdym request.
 * Od Spring Security 6.4 default to deferred (lazy) co psuje SPA pattern gdzie FE czyta cookie.
 * Stad ten filter (wzor z oficjalnej dokumentacji Spring Security).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // wywolanie getToken() materializuje token -> cookie jest zapisywany
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
