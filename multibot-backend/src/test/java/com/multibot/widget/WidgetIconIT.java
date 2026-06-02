package com.multibot.widget;

import com.multibot.integration.AuthenticatedIntegrationTestBase;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Własna ikonka widgetu + iconColor (tryb test = mock tenant = DEFAULT, owner zalogowany).
 */
class WidgetIconIT extends AuthenticatedIntegrationTestBase {

    // 1x1 transparentny PNG
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC");

    @Test
    @DisplayName("iconColor: PUT ustawia, GET zwraca")
    void iconColor_setAndGet() {
        given().contentType("application/json")
                .body("""
                        {"accentColor":"#7B5BFF","iconColor":"#16A34A","position":"bottom-right",
                         "title":"Bot","greeting":"Cześć","placeholder":"Napisz…","isActive":true}
                        """)
                .when().put("/api/admin/widget")
                .then().statusCode(200)
                .body("iconColor", equalTo("#16A34A"));

        given().when().get("/api/admin/widget")
                .then().statusCode(200)
                .body("settings.iconColor", equalTo("#16A34A"));
    }

    @Test
    @DisplayName("Upload ikonki (PNG) → iconUrl ustawiony, publiczny GET zwraca obrazek; DELETE czyści")
    void uploadServeDeleteIcon() {
        String publicKey = given().when().get("/api/admin/widget")
                .then().statusCode(200).extract().path("publicKey");

        // upload
        given().multiPart("file", "icon.png", PNG_1X1, "image/png")
                .when().post("/api/admin/widget/icon")
                .then().statusCode(200)
                .body("iconUrl", notNullValue())
                .body("iconUrl", startsWith("/api/widget/icon/" + publicKey));

        // publiczne serwowanie (bez auth — używamy czystego RestAssured, nie globalnego spec z cookie)
        RestAssured.given().when().get("/api/widget/icon/" + publicKey)
                .then().statusCode(200)
                .contentType(startsWith("image/png"));

        // delete → iconUrl null
        given().when().delete("/api/admin/widget/icon")
                .then().statusCode(200)
                .body("iconUrl", nullValue());
    }

    @Test
    @DisplayName("Upload nie-obrazka (magic bytes) → 400")
    void uploadNonImage_rejected() {
        given().multiPart("file", "evil.png", "<svg>nope</svg>".getBytes(), "image/png")
                .when().post("/api/admin/widget/icon")
                .then().statusCode(400);
    }
}
