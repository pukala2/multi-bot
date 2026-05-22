package com.multibot.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

class ApplicationStartupIT extends IntegrationTestBase {

    @Test
    @DisplayName("""
            Spring Boot context should start successfully with:
            1. Testcontainers PostgreSQL + pgvector
            2. Flyway applies V20260522_0001 (CREATE EXTENSION vector)
            3. All beans wire correctly (TenantContextFilter, MockTenantResolver, Spring AI, etc.)
            """)
    void shouldStartApplicationContext() {
    }

    @Test
    @DisplayName("Actuator liveness endpoint returns 200 UP")
    void shouldExposeLivenessEndpoint() {
        requestSpecification()
                .when()
                .get("/actuator/health/liveness")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
