package com.multibot.file.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoredFileIdTest {

    @Test
    void of_shouldWrapUuid() {
        UUID uuid = UUID.randomUUID();

        StoredFileId id = StoredFileId.of(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void of_shouldReject_null() {
        assertThatThrownBy(() -> StoredFileId.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void generate_shouldProduceUniqueIds() {
        StoredFileId id1 = StoredFileId.generate();
        StoredFileId id2 = StoredFileId.generate();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void generate_shouldProduceTimeSortedIds() throws InterruptedException {
        StoredFileId older = StoredFileId.generate();
        Thread.sleep(2);
        StoredFileId newer = StoredFileId.generate();

        assertThat(older.value().toString()).isLessThan(newer.value().toString());
    }
}
