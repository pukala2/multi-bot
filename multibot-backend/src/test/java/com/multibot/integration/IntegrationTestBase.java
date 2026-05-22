package com.multibot.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final Path TEST_STORAGE_DIR;

    static {
        POSTGRES = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("multibot_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        POSTGRES.start();

        try {
            TEST_STORAGE_DIR = Files.createTempDirectory("multibot-test-storage-");
            TEST_STORAGE_DIR.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test storage dir", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("multibot.file.storage.local.base-path", TEST_STORAGE_DIR::toString);
    }

    @LocalServerPort
    int port;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    public RequestSpecification requestSpecification() {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }
}
