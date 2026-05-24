package com.multibot.document.domain.model;

import com.multibot.file.domain.model.StoredFileId;
import com.multibot.shared.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    @Test
    void newlyUploaded_setsPendingStatus() {
        Document document = aPendingDocument();

        assertThat(document.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.indexedAt()).isEmpty();
        assertThat(document.errorMessage()).isEmpty();
    }

    @Test
    void withStatus_returnsNewInstance_withUpdatedStatus() {
        Document original = aPendingDocument();

        Document processing = original.withStatus(DocumentStatus.PROCESSING);

        assertThat(processing.status()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(original.status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void markAsReady_setsIndexedAt_clearsError() {
        Document original = aPendingDocument().markAsFailed("temporary error");
        Instant indexedAt = Instant.now();

        Document ready = original.markAsReady(indexedAt);

        assertThat(ready.status()).isEqualTo(DocumentStatus.READY);
        assertThat(ready.indexedAt()).contains(indexedAt);
        assertThat(ready.errorMessage()).isEmpty();
    }

    @Test
    void markAsFailed_setsErrorMessage() {
        Document original = aPendingDocument();

        Document failed = original.markAsFailed("Tika could not parse");

        assertThat(failed.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(failed.errorMessage()).contains("Tika could not parse");
    }

    @Test
    void shouldReject_blankFilename() {
        assertThatThrownBy(() -> new Document(
                DocumentId.generate(),
                TenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                StoredFileId.generate(),
                "",
                "application/pdf",
                100L,
                DocumentStatus.PENDING,
                Instant.now(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                Instant.now(),
                Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename");
    }

    @Test
    void shouldReject_nonPositiveSize() {
        assertThatThrownBy(() -> new Document(
                DocumentId.generate(),
                TenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                StoredFileId.generate(),
                "doc.pdf",
                "application/pdf",
                0L,
                DocumentStatus.PENDING,
                Instant.now(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                Instant.now(),
                Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sizeBytes");
    }

    private Document aPendingDocument() {
        Instant now = Instant.now();
        return Document.newlyUploaded(
                DocumentId.generate(),
                TenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                StoredFileId.generate(),
                "test.pdf",
                "application/pdf",
                1024L,
                now);
    }
}
