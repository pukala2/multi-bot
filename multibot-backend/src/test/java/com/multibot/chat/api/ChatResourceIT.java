package com.multibot.chat.api;

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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

class ChatResourceIT extends AuthenticatedIntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM chat_sessions");
        jdbc.update("DELETE FROM chunks");
        jdbc.update("DELETE FROM indexing_jobs");
        jdbc.update("DELETE FROM documents");
        jdbc.update("DELETE FROM stored_files");
    }

    @Test
    @DisplayName("POST /api/chat/sessions creates new session with 201 + Location")
    void shouldStartSession() {
        requestSpecification()
                .body("{}")
                .when()
                .post("/api/chat/sessions")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("anonymousId", notNullValue())
                .body("startedAt", notNullValue())
                .header("Location", notNullValue());
    }

    @Test
    @DisplayName("End-to-end: upload doc -> wait READY -> start session -> ask -> get blocking response")
    void shouldAnswerQuestion() {
        uploadDocumentAndWaitForReady();

        UUID sessionId = startSession();

        requestSpecification()
                .body("""
                        {"content": "Co to są chatboty?"}
                        """)
                .when()
                .post("/api/chat/sessions/{id}/messages", sessionId)
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("role", equalTo("ASSISTANT"))
                .body("content", notNullValue())
                .body("tokensUsed", greaterThan(0));

        Integer messageCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE session_id = ?",
                Integer.class, sessionId);
        org.assertj.core.api.Assertions.assertThat(messageCount).isEqualTo(2);
    }

    @Test
    @DisplayName("GET /api/chat/sessions/{id}/messages returns history")
    void shouldReturnHistory() {
        uploadDocumentAndWaitForReady();
        UUID sessionId = startSession();

        sendMessage(sessionId, "Pierwsze pytanie?");
        sendMessage(sessionId, "Drugie pytanie?");

        requestSpecification()
                .queryParam("size", 20)
                .when()
                .get("/api/chat/sessions/{id}/messages", sessionId)
                .then()
                .statusCode(200)
                .body("content.size()", equalTo(4))
                .body("pagination.totalElements", equalTo(4));
    }

    @Test
    @DisplayName("POST /messages with empty content returns 400 ValidationProblem")
    void shouldRejectEmptyContent() {
        UUID sessionId = startSession();

        requestSpecification()
                .body("""
                        {"content": ""}
                        """)
                .when()
                .post("/api/chat/sessions/{id}/messages", sessionId)
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /messages on non-existent session returns 404")
    void shouldReturn404ForMissingSession() {
        UUID nonExistent = UUID.randomUUID();

        requestSpecification()
                .body("""
                        {"content": "anything"}
                        """)
                .when()
                .post("/api/chat/sessions/{id}/messages", nonExistent)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("GET /api/chat/sessions returns paginated list")
    void shouldListSessions() {
        startSession();
        startSession();
        startSession();

        requestSpecification()
                .when()
                .get("/api/chat/sessions")
                .then()
                .statusCode(200)
                .body("content.size()", greaterThanOrEqualTo(3))
                .body("pagination.totalElements", greaterThanOrEqualTo(3));
    }

    private UUID uploadDocumentAndWaitForReady() {
        byte[] textBytes = """
                Chatboty są programami komputerowymi imitującymi konwersację z ludźmi.
                Wykorzystują przetwarzanie języka naturalnego i sztuczną inteligencję.
                Mogą odpowiadać na pytania klientów 24 godziny na dobę.
                """.repeat(15).getBytes(StandardCharsets.UTF_8);

        UUID documentId = UUID.fromString(RestAssured.given()
                .multiPart("file", "wiedza.txt", textBytes, "text/plain")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(202)
                .extract()
                .path("id"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        requestSpecification()
                                .when()
                                .get("/api/documents/{id}/status", documentId)
                                .then()
                                .body("status", equalTo("READY")));

        return documentId;
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

    private void sendMessage(UUID sessionId, String content) {
        requestSpecification()
                .body("{\"content\": \"" + content + "\"}")
                .when()
                .post("/api/chat/sessions/{id}/messages", sessionId)
                .then()
                .statusCode(200);
    }
}
