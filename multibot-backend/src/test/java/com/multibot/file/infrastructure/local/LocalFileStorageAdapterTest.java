package com.multibot.file.infrastructure.local;

import com.multibot.file.application.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        FileStorageProperties props = new FileStorageProperties(
                "local",
                new FileStorageProperties.Local(tempDir.toString()));
        adapter = new LocalFileStorageAdapter(props);
    }

    @Test
    void write_thenRead_returnsSameBytes() {
        byte[] bytes = "Hello multibot".getBytes(StandardCharsets.UTF_8);
        String key = "tenants/abc/files/test.txt";

        adapter.write(key, bytes);
        byte[] read = adapter.read(key);

        assertThat(read).isEqualTo(bytes);
    }

    @Test
    void write_createsNestedDirectories() throws IOException {
        byte[] bytes = "data".getBytes();
        String key = "tenants/some-tenant/files/some-id.pdf";

        adapter.write(key, bytes);

        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    void delete_removesFile() {
        byte[] bytes = "data".getBytes();
        String key = "tenants/abc/files/temp.txt";
        adapter.write(key, bytes);

        adapter.delete(key);

        assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }

    @Test
    void delete_isIdempotent_whenFileMissing() {
        String key = "tenants/abc/files/never-existed.txt";

        adapter.delete(key);

        assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }

    @Test
    void resolve_rejectsPathEscape() {
        byte[] bytes = "evil".getBytes();
        String maliciousKey = "../../etc/passwd";

        assertThatThrownBy(() -> adapter.write(maliciousKey, bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes base path");
    }
}
