package com.multibot.chat.api;

import com.multibot.integration.AuthenticatedIntegrationTestBase;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStreamingIT extends AuthenticatedIntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("POST /messages with Accept: text/event-stream returns SSE with chunk + done events")
    void shouldStreamMessageViaSSE() {
        UUID sessionId = startSession();

        String body = RestAssured.given()
                .accept("text/event-stream")
                .contentType("application/json")
                .body("""
                        {"content": "Co to są chatboty?"}
                        """)
                .when()
                .post("/api/chat/sessions/{id}/messages", sessionId)
                .then()
                .statusCode(200)
                .contentType(org.hamcrest.Matchers.startsWith("text/event-stream"))
                .extract()
                .asString();

        assertThat(body).contains("event:chunk");
        assertThat(body).contains("event:sources");
        assertThat(body).contains("event:done");
        assertThat(body).contains("messageId");
        assertThat(body).contains("tokensUsed");

        Integer messageCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE session_id = ?",
                Integer.class, sessionId);
        assertThat(messageCount).isEqualTo(2);
    }

    @Test
    @DisplayName("POST /messages with Accept: application/json still returns blocking JSON response")
    void shouldStillSupportBlockingJsonResponse() {
        UUID sessionId = startSession();

        RestAssured.given()
                .accept("application/json")
                .contentType("application/json")
                .body("""
                        {"content": "Co tu jest?"}
                        """)
                .when()
                .post("/api/chat/sessions/{id}/messages", sessionId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("role", org.hamcrest.Matchers.equalTo("ASSISTANT"));
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
