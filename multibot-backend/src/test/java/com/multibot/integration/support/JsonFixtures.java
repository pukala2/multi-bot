package com.multibot.integration.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class JsonFixtures {

    private static final String FIXTURES_ROOT = "src/test/resources/__files/";

    private JsonFixtures() {
    }

    public static String load(String fileName) {
        try {
            return Files.readString(Paths.get(FIXTURES_ROOT + fileName));
        } catch (IOException e) {
            throw new RuntimeException("Cannot load fixture: " + fileName, e);
        }
    }
}
