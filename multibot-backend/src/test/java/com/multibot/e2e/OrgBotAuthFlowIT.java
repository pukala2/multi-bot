package com.multibot.e2e;

import com.multibot.integration.IntegrationTestBase;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * E2E flow modelu Organizacja -> Boty w trybie auth (Spring Security + sesje + CSRF).
 * Bootstrap (z application.yml) tworzy super admina i ownera domyslnej organizacji.
 */
@TestPropertySource(properties = "multibot.tenancy.mode=auth")
class OrgBotAuthFlowIT extends IntegrationTestBase {

    private static final String SA_EMAIL = "superadmin@multibot.local";
    private static final String SA_PASS = "Multibot!2026";
    private static final String OWNER_EMAIL = "owner@demo.local";
    private static final String OWNER_PASS = "Demo!Owner2026";
    private static final UUID DEFAULT_BOT = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Test
    @DisplayName("Super admin loguje się — /me bez organizacji i bez botów")
    void superAdminLogin_meHasNoOrg() {
        Sess s = loginAs(SA_EMAIL, SA_PASS);

        given().filter(s.cookies()).when().get("/api/auth/me").then()
                .statusCode(200)
                .body("role", equalTo("SUPER_ADMIN"))
                .body("organizationId", nullValue())
                .body("bots.size()", equalTo(0))
                .body("activeBot", nullValue());
    }

    @Test
    @DisplayName("Owner domyślnej organizacji ma 1 bota, który jest aktywny")
    void defaultOwnerLogin_hasSingleActiveBot() {
        Sess s = loginAs(OWNER_EMAIL, OWNER_PASS);

        given().filter(s.cookies()).when().get("/api/auth/me").then()
                .statusCode(200)
                .body("role", equalTo("OWNER"))
                .body("organizationId", notNullValue())
                .body("bots.size()", equalTo(1))
                .body("activeBot.name", equalTo("Default Tenant"));
    }

    @Test
    @DisplayName("Błędne hasło → 401")
    void badCredentials_returns401() {
        CookieFilter cj = new CookieFilter();
        String csrf = initialCsrf(cj);

        given().filter(cj).header("X-XSRF-TOKEN", csrf).contentType(ContentType.JSON)
                .body(loginBody(OWNER_EMAIL, "zle-haslo"))
                .when().post("/api/auth/login").then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Super admin tworzy org (BASIC) + 2 boty; owner przełącza aktywnego bota; obcy bot → 403; limit pooled = 2000")
    void provisionOrg_switchBot_pooledLimit() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + suffix + "@test.local";

        Sess sa = loginAs(SA_EMAIL, SA_PASS);

        // utworz organizacje + pierwszy bot
        ExtractableResponse<Response> created = given().filter(sa.cookies())
                .header("X-XSRF-TOKEN", sa.csrf()).contentType(ContentType.JSON)
                .body("""
                        {"name":"Org %s","ownerEmail":"%s","plan":"BASIC","firstBotName":"Bot A"}
                        """.formatted(suffix, ownerEmail))
                .when().post("/api/super-admin/organizations").then()
                .statusCode(201)
                .body("generatedPassword", notNullValue())
                .body("widgetKey", startsWith("pk_widget_"))
                .extract();
        String orgId = created.path("organizationId");
        String genPass = created.path("generatedPassword");

        // dodaj drugiego bota
        given().filter(sa.cookies()).header("X-XSRF-TOKEN", sa.csrf()).contentType(ContentType.JSON)
                .body("{\"name\":\"Bot B\"}")
                .when().post("/api/super-admin/organizations/{id}/bots", orgId).then()
                .statusCode(201)
                .body("widgetKey", startsWith("pk_widget_"));

        // owner loguje sie -> widzi 2 boty, aktywny = Bot A
        Sess owner = loginAs(ownerEmail, genPass);
        ExtractableResponse<Response> me = given().filter(owner.cookies()).when().get("/api/auth/me").then()
                .statusCode(200)
                .body("bots.size()", equalTo(2))
                .body("activeBot.name", equalTo("Bot A"))
                .extract();
        String botBId = me.path("bots.find { it.name == 'Bot B' }.id");

        // przelacz aktywnego bota na Bot B
        given().filter(owner.cookies()).header("X-XSRF-TOKEN", owner.csrf()).contentType(ContentType.JSON)
                .body("{\"tenantId\":\"" + botBId + "\"}")
                .when().post("/api/auth/active-bot").then()
                .statusCode(200)
                .body("name", equalTo("Bot B"));

        given().filter(owner.cookies()).when().get("/api/auth/me").then()
                .body("activeBot.name", equalTo("Bot B"));

        // probuje przelaczyc na bota z OBCEJ organizacji -> 403
        given().filter(owner.cookies()).header("X-XSRF-TOKEN", owner.csrf()).contentType(ContentType.JSON)
                .body("{\"tenantId\":\"" + DEFAULT_BOT + "\"}")
                .when().post("/api/auth/active-bot").then()
                .statusCode(403);

        // limit wspolny (pooled) z planu organizacji BASIC = 2000
        given().filter(owner.cookies()).when().get("/api/admin/billing/usage").then()
                .statusCode(200)
                .body("plan", equalTo("BASIC"))
                .body("effectiveLimit", equalTo(2000))
                .body("userMessages", equalTo(0));
    }

    @Test
    @DisplayName("Self-serve: owner dodaje bota do limitu planu (BASIC=2), potem 409; super admin omija limit")
    void ownerSelfServe_enforcesPlanLimit_superAdminBypasses() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + suffix + "@test.local";

        Sess sa = loginAs(SA_EMAIL, SA_PASS);
        ExtractableResponse<Response> created = given().filter(sa.cookies())
                .header("X-XSRF-TOKEN", sa.csrf()).contentType(ContentType.JSON)
                .body("""
                        {"name":"Org %s","ownerEmail":"%s","plan":"BASIC","firstBotName":"Bot A"}
                        """.formatted(suffix, ownerEmail))
                .when().post("/api/super-admin/organizations").then()
                .statusCode(201).extract();
        String orgId = created.path("organizationId");
        String genPass = created.path("generatedPassword");

        Sess owner = loginAs(ownerEmail, genPass);

        // 1/2 -> dozwolone
        given().filter(owner.cookies()).when().get("/api/bots/limit").then()
                .statusCode(200)
                .body("currentBots", equalTo(1))
                .body("maxBots", equalTo(2))
                .body("allowed", equalTo(true));

        // owner dodaje 2. bota -> 201
        given().filter(owner.cookies()).header("X-XSRF-TOKEN", owner.csrf()).contentType(ContentType.JSON)
                .body("{\"name\":\"Bot B\"}")
                .when().post("/api/bots").then()
                .statusCode(201)
                .body("widgetKey", startsWith("pk_widget_"));

        // 2/2 -> zablokowane
        given().filter(owner.cookies()).when().get("/api/bots/limit").then()
                .statusCode(200)
                .body("currentBots", equalTo(2))
                .body("allowed", equalTo(false));

        // owner próbuje 3. bota -> 409
        given().filter(owner.cookies()).header("X-XSRF-TOKEN", owner.csrf()).contentType(ContentType.JSON)
                .body("{\"name\":\"Bot C\"}")
                .when().post("/api/bots").then()
                .statusCode(409);

        // super admin omija limit -> dodaje 3. bota przez /api/super-admin
        given().filter(sa.cookies()).header("X-XSRF-TOKEN", sa.csrf()).contentType(ContentType.JSON)
                .body("{\"name\":\"Bot C (admin)\"}")
                .when().post("/api/super-admin/organizations/{id}/bots", orgId).then()
                .statusCode(201);
    }

    @Test
    @DisplayName("Self-serve: plan FREE = 1 bot → owner od razu nie może dodać (409)")
    void ownerSelfServe_freePlan_singleBotLimit() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-free-" + suffix + "@test.local";

        Sess sa = loginAs(SA_EMAIL, SA_PASS);
        String genPass = given().filter(sa.cookies())
                .header("X-XSRF-TOKEN", sa.csrf()).contentType(ContentType.JSON)
                .body("""
                        {"name":"Free Org %s","ownerEmail":"%s","plan":"FREE"}
                        """.formatted(suffix, ownerEmail))
                .when().post("/api/super-admin/organizations").then()
                .statusCode(201).extract().path("generatedPassword");

        Sess owner = loginAs(ownerEmail, genPass);

        given().filter(owner.cookies()).when().get("/api/bots/limit").then()
                .statusCode(200)
                .body("currentBots", equalTo(1))
                .body("maxBots", equalTo(1))
                .body("allowed", equalTo(false));

        given().filter(owner.cookies()).header("X-XSRF-TOKEN", owner.csrf()).contentType(ContentType.JSON)
                .body("{\"name\":\"Druga strona\"}")
                .when().post("/api/bots").then()
                .statusCode(409);
    }

    @Test
    @DisplayName("Dozwolone domeny widgetu: PUT normalizuje URL->origin, GET zwraca zapisane")
    void widgetAllowedOrigins_normalizedAndPersisted() {
        Sess owner = loginAs(OWNER_EMAIL, OWNER_PASS);

        given().filter(owner.cookies()).header("X-XSRF-TOKEN", owner.csrf()).contentType(ContentType.JSON)
                .body("{\"origins\":[\"https://example.com\",\"example.org/sklep\"]}")
                .when().put("/api/admin/widget/origins").then()
                .statusCode(200)
                .body("origins", hasItems("https://example.com", "https://example.org"));

        given().filter(owner.cookies()).when().get("/api/admin/widget/origins").then()
                .statusCode(200)
                .body("origins", hasItems("https://example.com", "https://example.org"));
    }

    @Test
    @DisplayName("Utworzenie org z firstBotUrl ustawia allowed_origins pierwszego bota")
    void provisionWithBotUrl_setsAllowedOrigins() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-url-" + suffix + "@test.local";

        Sess sa = loginAs(SA_EMAIL, SA_PASS);
        String pass = given().filter(sa.cookies()).header("X-XSRF-TOKEN", sa.csrf()).contentType(ContentType.JSON)
                .body("""
                        {"name":"Org URL %s","ownerEmail":"%s","plan":"PRO","firstBotName":"Bot A","firstBotUrl":"silesiaszymanow.pl"}
                        """.formatted(suffix, ownerEmail))
                .when().post("/api/super-admin/organizations").then()
                .statusCode(201).extract().path("generatedPassword");

        // owner loguje sie -> aktywny bot = Bot A (z firstBotUrl) -> origins ustawione (znormalizowane)
        Sess owner = loginAs(ownerEmail, pass);
        given().filter(owner.cookies()).when().get("/api/admin/widget/origins").then()
                .statusCode(200)
                .body("origins", hasItem("https://silesiaszymanow.pl"));
    }

    @Test
    @DisplayName("Portfel AI per dostawca: doładowanie OPENAI zwiększa total; total = suma portfeli; owner dostaje 403")
    void aiCredit_topupPerProvider() {
        // owner (nie super admin) -> 403
        Sess owner = loginAs(OWNER_EMAIL, OWNER_PASS);
        given().filter(owner.cookies()).when().get("/api/super-admin/ai-credit").then().statusCode(403);

        Sess sa = loginAs(SA_EMAIL, SA_PASS);
        ExtractableResponse<Response> initial = given().filter(sa.cookies()).when().get("/api/super-admin/ai-credit").then()
                .statusCode(200)
                .body("wallets.size()", equalTo(2))
                .body("wallets.provider", hasItems("OPENAI", "ANTHROPIC"))
                .extract();
        float beforeTotal = num(initial, "totalToppedUp");

        ExtractableResponse<Response> after = given().filter(sa.cookies())
                .header("X-XSRF-TOKEN", sa.csrf()).contentType(ContentType.JSON)
                .body("{\"provider\":\"OPENAI\",\"amount\":5.00,\"note\":\"OpenAI 01.06\"}")
                .when().post("/api/super-admin/ai-credit/topups").then()
                .statusCode(200)
                .body("topups.find { it.provider == 'OPENAI' }.amountUsd", notNullValue())
                .extract();

        float totalToppedUp = num(after, "totalToppedUp");
        float totalSpent = num(after, "totalSpent");
        float totalRemaining = num(after, "totalRemaining");
        float openaiTopped = num(after, "wallets.find { it.provider == 'OPENAI' }.toppedUp");
        assertThat(totalToppedUp).isCloseTo(beforeTotal + 5f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(openaiTopped).isGreaterThanOrEqualTo(5f);
        assertThat(totalRemaining).isCloseTo(totalToppedUp - totalSpent, org.assertj.core.data.Offset.offset(0.001f));
    }

    // --- helpers ---

    /** JSON number → float (RestAssured zwraca Integer dla 0, Float dla 5.0 — ujednolicamy). */
    private static float num(ExtractableResponse<Response> resp, String path) {
        return ((Number) resp.path(path)).floatValue();
    }

    /** Sesja zalogowanego usera: jar ciasteczek + aktualny token CSRF (po rotacji przy loginie). */
    private record Sess(CookieFilter cookies, String csrf) {
    }

    private Sess loginAs(String email, String password) {
        CookieFilter cj = new CookieFilter();
        String csrf0 = initialCsrf(cj);
        // Login rotuje token CSRF -> nowy token jest w Set-Cookie odpowiedzi logowania.
        String rotated = given().filter(cj).header("X-XSRF-TOKEN", csrf0).contentType(ContentType.JSON)
                .body(loginBody(email, password))
                .when().post("/api/auth/login").then()
                .statusCode(200)
                .extract().cookie("XSRF-TOKEN");
        return new Sess(cj, rotated != null ? rotated : csrf0);
    }

    /** Pierwszy GET (bez ciasteczka) wymusza wygenerowanie i Set-Cookie tokenu CSRF. */
    private String initialCsrf(CookieFilter cj) {
        return given().filter(cj).when().get("/api/auth/me").then().extract().cookie("XSRF-TOKEN");
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
