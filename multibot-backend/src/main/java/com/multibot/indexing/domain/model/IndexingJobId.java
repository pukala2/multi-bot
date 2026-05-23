package com.multibot.indexing.domain.model;

import com.multibot.shared.util.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

public record IndexingJobId(UUID value) {

    public IndexingJobId {
        Objects.requireNonNull(value, "IndexingJobId value must not be null");
    }

    public static IndexingJobId of(UUID value) {
        return new IndexingJobId(value);
    }

    public static IndexingJobId generate() {
        return new IndexingJobId(UuidGenerator.newUuidV7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
