package com.multibot.document.api;

import com.multibot.integration.IntegrationTestBase;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

class DocumentResourceIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDocumentsTable() {
        jdbc.update("DELETE FROM documents");
        jdbc.update("DELETE FROM stored_files");
    }

    @Test
    @DisplayName("POST /api/documents returns 202 Accepted with PENDING status + Location header")
    void shouldUploadDocument() {
        byte[] pdfBytes = "PDF-FAKE-CONTENT".getBytes(StandardCharsets.UTF_8);

        String location = RestAssured.given()
                .multiPart("file", "owu.pdf", pdfBytes, "application/pdf")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(202)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("filename", equalTo("owu.pdf"))
                .body("mimeType", equalTo("application/pdf"))
                .body("sizeBytes", equalTo(pdfBytes.length))
                .body("status", equalTo("PENDING"))
                .body("uploadedAt", notNullValue())
                .header("Location", matchesPattern(".*/api/documents/[0-9a-f-]{36}"))
                .extract()
                .header("Location");

        assertThat(location).isNotBlank();
    }

    @Test
    @DisplayName("POST /api/documents with empty file returns 400 ProblemDetail")
    void shouldRejectEmptyFile() {
        RestAssured.given()
                .multiPart("file", "empty.pdf", new byte[0], "application/pdf")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("title", equalTo("Validation Failed"))
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/documents with unsupported extension returns 400")
    void shouldRejectUnsupportedExtension() {
        RestAssured.given()
                .multiPart("file", "malware.exe", "EXE".getBytes(), "application/x-msdownload")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("detail", org.hamcrest.Matchers.containsString("Unsupported file extension"));
    }

    @Test
    @DisplayName("POST /api/documents with unsupported mime type returns 400")
    void shouldRejectUnsupportedMimeType() {
        RestAssured.given()
                .multiPart("file", "image.pdf", "FAKE".getBytes(), "image/jpeg")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("detail", org.hamcrest.Matchers.containsString("Unsupported content type"));
    }

    @Test
    @DisplayName("POST /api/documents accepts .docx file with proper mime type")
    void shouldAcceptDocx() {
        RestAssured.given()
                .multiPart("file", "report.docx", "DOCX".getBytes(),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(202)
                .body("filename", equalTo("report.docx"));
    }

    @Test
    @DisplayName("POST /api/documents accepts .md file")
    void shouldAcceptMarkdown() {
        RestAssured.given()
                .multiPart("file", "README.md", "# Hello".getBytes(), "text/markdown")
                .when()
                .post("/api/documents")
                .then()
                .statusCode(202)
                .body("filename", equalTo("README.md"));
    }

    @Test
    @DisplayName("GET /api/documents/{id} returns full document metadata")
    void shouldGetDocumentById() {
        UUID id = uploadDocument("greeting.txt", "text/plain", "hello".getBytes());

        requestSpecification()
                .when()
                .get("/api/documents/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id.toString()))
                .body("filename", equalTo("greeting.txt"))
                .body("mimeType", equalTo("text/plain"));
    }

    @Test
    @DisplayName("GET /api/documents/{id} returns 404 for unknown id")
    void shouldReturn404ForMissingDocument() {
        UUID missingId = UUID.randomUUID();

        requestSpecification()
                .when()
                .get("/api/documents/{id}", missingId)
                .then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/documents/{id}/status returns lightweight status response")
    void shouldGetDocumentStatus() {
        UUID id = uploadDocument("doc.pdf", "application/pdf", "PDF".getBytes());

        requestSpecification()
                .when()
                .get("/api/documents/{id}/status", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id.toString()))
                .body("status", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @DisplayName("GET /api/documents returns paginated list with metadata")
    void shouldListDocuments() {
        uploadDocument("file1.pdf", "application/pdf", "A".getBytes());
        uploadDocument("file2.pdf", "application/pdf", "B".getBytes());
        uploadDocument("file3.pdf", "application/pdf", "C".getBytes());

        requestSpecification()
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/documents")
                .then()
                .statusCode(200)
                .body("content", notNullValue())
                .body("content.size()", greaterThanOrEqualTo(3))
                .body("pagination.page", equalTo(0))
                .body("pagination.size", equalTo(10))
                .body("pagination.totalElements", greaterThanOrEqualTo(3));
    }

    @Test
    @DisplayName("DELETE /api/documents/{id} returns 204 and removes document")
    void shouldDeleteDocument() {
        UUID id = uploadDocument("temp.pdf", "application/pdf", "data".getBytes());

        RestAssured.given()
                .accept("*/*")
                .when()
                .delete("/api/documents/{id}", id)
                .then()
                .statusCode(204);

        requestSpecification()
                .when()
                .get("/api/documents/{id}", id)
                .then()
                .statusCode(404);
    }

    private UUID uploadDocument(String filename, String mimeType, byte[] bytes) {
        String idString = RestAssured.given()
                .multiPart("file", filename, bytes, mimeType)
                .when()
                .post("/api/documents")
                .then()
                .statusCode(202)
                .extract()
                .path("id");
        return UUID.fromString(idString);
    }
}
