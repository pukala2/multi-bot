package com.multibot.shared.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

public final class UuidGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidGenerator() {
    }

    public static UUID newUuidV7() {
        return GENERATOR.generate();
    }
}
