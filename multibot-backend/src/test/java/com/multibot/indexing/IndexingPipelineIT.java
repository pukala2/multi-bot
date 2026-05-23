package com.multibot.indexing;

import com.multibot.integration.IntegrationTestBase;
import io.restassured.RestAssured;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class IndexingPipelineIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Upload triggers async indexing; document transitions PENDING -> READY; chunks persisted")
    void shouldIndexDocumentAfterUpload() {
        jdbc.update("DELETE FROM chunks");
        jdbc.update("DELETE FROM indexing_jobs");
        jdbc.update("DELETE FROM documents");
        jdbc.update("DELETE FROM stored_files");

        byte[] textBytes = """
                This is a long piece of text containing meaningful content about chatbots.
                Chatbots are AI systems that converse with users in natural language.
                Modern chatbots use large language models to generate responses.
                Retrieval Augmented Generation enhances chatbots with custom knowledge bases.
                """.repeat(20).getBytes(StandardCharsets.UTF_8);

        String documentId = RestAssured.given()
                .multiPart("file", "knowledge.txt", textBytes, "text/plain")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(202)
                .body("status", equalTo("PENDING"))
                .extract()
                .path("id");

        UUID docUuid = UUID.fromString(documentId);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        requestSpecification()
                                .when()
                                .get("/api/documents/{id}/status", docUuid)
                                .then()
                                .statusCode(200)
                                .body("status", equalTo("READY")));

        Integer chunkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ?",
                Integer.class, docUuid);
        assertThat(chunkCount).isGreaterThan(0);

        String jobStatus = jdbc.queryForObject(
                "SELECT status FROM indexing_jobs WHERE document_id = ?",
                String.class, docUuid);
        assertThat(jobStatus).isEqualTo("COMPLETED");
    }
}
