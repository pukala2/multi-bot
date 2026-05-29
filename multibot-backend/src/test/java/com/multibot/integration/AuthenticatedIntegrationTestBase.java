package com.multibot.integration;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Baza dla ITów uderzających w HTTP, które wymagają zalogowanego użytkownika (Spring Security
 * jest aktywne także w profilu test). Loguje się jako bootstrapowany owner domyślnej organizacji
 * i ustawia GLOBALNY RestAssured spec (ciasteczka sesji + token CSRF), więc wszystkie
 * given()/requestSpecification() w testach są uwierzytelnione.
 *
 * W profilu test (multibot.tenancy.mode=mock) MockTenantResolver i tak zwraca DEFAULT tenant,
 * więc testy operują na tenantcie domyślnym — zgodnie z ich dotychczasowymi założeniami.
 */
public abstract class AuthenticatedIntegrationTestBase extends IntegrationTestBase {

    private static final String OWNER_EMAIL = "owner@demo.local";
    private static final String OWNER_PASSWORD = "Demo!Owner2026";

    @BeforeEach
    void authenticate() {
        RestAssured.requestSpecification = null;   // czysty start przed loginem

        CookieFilter cookies = new CookieFilter();
        String csrf0 = RestAssured.given().filter(cookies)
                .when().get("/api/auth/me")
                .then().extract().cookie("XSRF-TOKEN");

        // Login rotuje token CSRF -> nowy token w Set-Cookie odpowiedzi logowania (fallback do csrf0).
        String rotated = RestAssured.given().filter(cookies)
                .header("X-XSRF-TOKEN", csrf0).contentType(ContentType.JSON)
                .body("{\"email\":\"" + OWNER_EMAIL + "\",\"password\":\"" + OWNER_PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie("XSRF-TOKEN");
        String csrf = rotated != null ? rotated : csrf0;

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .addFilter(cookies)
                .addHeader("X-XSRF-TOKEN", csrf)
                .build();
    }

    @AfterEach
    void clearAuthSpec() {
        RestAssured.requestSpecification = null;   // brak wycieku auth do innych klas testowych
    }
}
