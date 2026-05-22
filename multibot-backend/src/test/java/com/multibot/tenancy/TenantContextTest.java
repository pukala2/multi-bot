package com.multibot.tenancy;

import com.multibot.shared.tenant.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void tryGet_returnsEmpty_whenNotSet() {
        assertThat(TenantContext.tryGet()).isEmpty();
    }

    @Test
    void get_throws_whenNotSet() {
        assertThatThrownBy(TenantContext::get)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void set_then_get_returnsValue() {
        var tenantId = TenantId.of(UUID.randomUUID());

        TenantContext.set(tenantId);

        assertThat(TenantContext.get()).isEqualTo(tenantId);
        assertThat(TenantContext.tryGet()).contains(tenantId);
    }

    @Test
    void clear_removesValue() {
        TenantContext.set(TenantId.of(UUID.randomUUID()));

        TenantContext.clear();

        assertThat(TenantContext.tryGet()).isEmpty();
    }

    @Test
    void threadLocal_isolation() throws InterruptedException {
        var mainThreadId = TenantId.of(UUID.randomUUID());
        TenantContext.set(mainThreadId);

        var otherThreadResult = new AtomicReference<TenantId>();
        var thread = new Thread(() -> otherThreadResult.set(TenantContext.tryGet().orElse(null)));
        thread.start();
        thread.join();

        assertThat(otherThreadResult.get()).isNull();
        assertThat(TenantContext.get()).isEqualTo(mainThreadId);
    }
}
