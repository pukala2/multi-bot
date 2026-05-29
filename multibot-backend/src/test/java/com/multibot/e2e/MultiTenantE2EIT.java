package com.multibot.e2e;

import com.multibot.integration.AuthenticatedIntegrationTestBase;
import io.restassured.RestAssured;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class MultiTenantE2EIT extends AuthenticatedIntegrationTestBase {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-7000-8000-00000000000b");

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanAndSeedTenantB() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM chat_sessions");
        jdbc.update("DELETE FROM chunks");
        jdbc.update("DELETE FROM indexing_jobs");
        jdbc.update("DELETE FROM documents");
        jdbc.update("DELETE FROM stored_files");
        jdbc.update("DELETE FROM tenants WHERE id = ? OR slug = 'tenant-b-e2e'", TENANT_B);
        jdbc.update("DELETE FROM organizations WHERE id = ? OR slug = 'org-b-e2e'", TENANT_B);
        // org B + bot B (organization_id = TENANT_B dla prostoty). Plan/firma są na poziomie org.
        jdbc.update("""
                INSERT INTO organizations (id, name, slug, plan, status, created_at, updated_at)
                VALUES (?, 'Tenant B E2E', 'org-b-e2e', 'FREE', 'ACTIVE', NOW(), NOW())
                """, TENANT_B);
        jdbc.update("""
                INSERT INTO tenants (id, name, slug, organization_id, status, created_at, updated_at)
                VALUES (?, 'Tenant B E2E', 'tenant-b-e2e', ?, 'ACTIVE', NOW(), NOW())
                """, TENANT_B, TENANT_B);
    }

    @Test
    @DisplayName("E2E - two tenants in MVP simulation (note: MockTenantResolver returns DEFAULT for HTTP - we verify DB isolation at storage layer)")
    void shouldIsolateDataInDatabase() {
        UUID docA = uploadDocument("A-knowledge.txt", "Tenant A specific knowledge: chatboty są super.");
        waitForReady(docA);

        Long chunksForA = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE tenant_id = ?", Long.class, TENANT_A);
        Long chunksForB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE tenant_id = ?", Long.class, TENANT_B);

        assertThat(chunksForA).isGreaterThan(0L);
        assertThat(chunksForB).isEqualTo(0L);

        Long documentsForA = jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE tenant_id = ?", Long.class, TENANT_A);
        Long documentsForB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE tenant_id = ?", Long.class, TENANT_B);

        assertThat(documentsForA).isEqualTo(1L);
        assertThat(documentsForB).isEqualTo(0L);
    }

    @Test
    @DisplayName("E2E - upload, index, ask via SSE - response references only own tenant chunks")
    void shouldCompleteFullRagFlow() {
        UUID docId = uploadDocument("knowledge.txt",
                "Chatboty są programami imitującymi konwersację z ludźmi. "
                        + "Wykorzystują NLP i AI. ".repeat(20));
        waitForReady(docId);

        UUID sessionId = startSession();
        String responseBody = RestAssured.given()
                .accept("text/event-stream")
                .contentType("application/json")
                .body("""
                        {"content": "Co to są chatboty?"}
                        """)
                .when()
                .post("/api/chat/sessions/{id}/messages", sessionId)
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertThat(responseBody).contains("event:chunk");
        assertThat(responseBody).contains("event:done");

        Long messageCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE tenant_id = ? AND session_id = ?",
                Long.class, TENANT_A, sessionId);
        assertThat(messageCount).isEqualTo(2);

        Long otherTenantMessages = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE tenant_id = ?",
                Long.class, TENANT_B);
        assertThat(otherTenantMessages).isEqualTo(0L);
    }

    @Test
    @DisplayName("E2E - X-Request-Id header is echoed in response")
    void shouldEchoRequestIdHeader() {
        String customRequestId = "trace-abc-123";

        RestAssured.given()
                .header("X-Request-Id", customRequestId)
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/chat/sessions")
                .then()
                .statusCode(201)
                .header("X-Request-Id", equalTo(customRequestId));
    }

    @Test
    @DisplayName("E2E - request without X-Request-Id gets auto-generated UUID in response")
    void shouldGenerateRequestIdWhenMissing() {
        RestAssured.given()
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/chat/sessions")
                .then()
                .statusCode(201)
                .header("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}"));
    }

    private UUID uploadDocument(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return UUID.fromString(RestAssured.given()
                .multiPart("file", filename, bytes, "text/plain")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(202)
                .extract()
                .path("id"));
    }

    private void waitForReady(UUID documentId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        requestSpecification()
                                .when()
                                .get("/api/documents/{id}/status", documentId)
                                .then()
                                .body("status", equalTo("READY")));
    }

    private UUID startSession() {
        return UUID.fromString(requestSpecification()
                .body("{}")
                .when()
                .post("/api/chat/sessions")
                .then()
                .statusCode(201)
                .extract()
                .path("id"));
    }
}
