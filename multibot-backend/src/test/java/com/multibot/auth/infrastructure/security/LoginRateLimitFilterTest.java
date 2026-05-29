package com.multibot.auth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    private static MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    private static boolean chainProceeded(MockFilterChain chain) {
        return chain.getRequest() != null;
    }

    @Test
    void przepuszcza_do_limitu_potem_429() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2);

        for (int i = 1; i <= 2; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(loginRequest("1.2.3.4"), res, chain);
            assertThat(chainProceeded(chain)).as("próba %d przepuszczona", i).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(loginRequest("1.2.3.4"), blocked, chain);

        assertThat(chainProceeded(chain)).as("3. próba zablokowana").isFalse();
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
        assertThat(blocked.getContentAsString()).contains("TOO_MANY_LOGIN_ATTEMPTS");
    }

    @Test
    void limit_jest_per_ip() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(1);

        MockFilterChain c1 = new MockFilterChain();
        filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), c1);
        assertThat(chainProceeded(c1)).isTrue();

        // Inny IP ma własny bucket — nie jest zablokowany przez ruch pierwszego.
        MockFilterChain c2 = new MockFilterChain();
        filter.doFilter(loginRequest("10.0.0.2"), new MockHttpServletResponse(), c2);
        assertThat(chainProceeded(c2)).isTrue();

        // Pierwszy IP po wyczerpaniu limitu -> 429.
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain c3 = new MockFilterChain();
        filter.doFilter(loginRequest("10.0.0.1"), res, c3);
        assertThat(chainProceeded(c3)).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void nie_dotyczy_innych_sciezek() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(1);

        // GET /api/auth/me wielokrotnie — nigdy nie throttlowane.
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/me");
            req.setRemoteAddr("1.1.1.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertThat(chainProceeded(chain)).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }
}
