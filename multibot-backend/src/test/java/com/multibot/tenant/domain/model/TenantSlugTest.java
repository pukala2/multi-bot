package com.multibot.tenant.domain.model;

import com.multibot.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantSlugTest {

    @ParameterizedTest
    @ValueSource(strings = {"acme", "acme-corp", "a-b-c-d", "company-123", "test-tenant-2025"})
    void shouldAccept_validSlugs(String value) {
        TenantSlug slug = TenantSlug.of(value);

        assertThat(slug.value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "x", ""})
    void shouldReject_tooShortSlug(String value) {
        assertThatThrownBy(() -> TenantSlug.of(value))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Slug must be between");
    }

    @Test
    void shouldReject_tooLongSlug() {
        String tooLong = "a".repeat(65);

        assertThatThrownBy(() -> TenantSlug.of(tooLong))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Slug must be between");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACME", "Acme-Corp", "acme_corp", "acme corp", "acme!", "-acme", "acme-", "acme--corp"})
    void shouldReject_invalidPattern(String value) {
        assertThatThrownBy(() -> TenantSlug.of(value))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("lowercase alphanumeric");
    }

    @Test
    void shouldReject_nullValue() {
        assertThatThrownBy(() -> TenantSlug.of(null))
                .isInstanceOf(NullPointerException.class);
    }
}
