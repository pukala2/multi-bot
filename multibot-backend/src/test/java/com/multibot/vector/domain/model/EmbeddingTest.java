package com.multibot.vector.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingTest {

    @Test
    void shouldAccept_correctDimensions() {
        float[] values = new float[Embedding.DIMENSIONS];

        Embedding embedding = Embedding.of(values);

        assertThat(embedding.values()).hasSize(Embedding.DIMENSIONS);
    }

    @Test
    void shouldReject_tooFewDimensions() {
        float[] tooFew = new float[100];

        assertThatThrownBy(() -> Embedding.of(tooFew))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 1536 dimensions");
    }

    @Test
    void shouldReject_tooManyDimensions() {
        float[] tooMany = new float[2048];

        assertThatThrownBy(() -> Embedding.of(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 1536");
    }

    @Test
    void shouldReject_null() {
        assertThatThrownBy(() -> Embedding.of(null))
                .isInstanceOf(NullPointerException.class);
    }
}
