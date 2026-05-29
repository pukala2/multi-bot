package com.multibot.tenant;

import com.multibot.integration.IntegrationTestBase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.TenantSlug;
import com.multibot.tenant.domain.model.TenantStatus;
import com.multibot.tenant.domain.port.in.GetTenantUseCase;
import com.multibot.tenant.domain.port.out.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantPersistenceIT extends IntegrationTestBase {

    private static final TenantId DEFAULT_TENANT_ID = TenantId.of(
            UUID.fromString("00000000-0000-7000-8000-000000000001"));

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    GetTenantUseCase getTenantUseCase;

    @Test
    @DisplayName("Flyway seed creates default tenant accessible via TenantRepository")
    void shouldFindDefaultTenant_seededByFlyway() {
        var tenant = tenantRepository.findById(DEFAULT_TENANT_ID);

        assertThat(tenant).isPresent();
        assertThat(tenant.get().name()).isEqualTo("Default Tenant");
        assertThat(tenant.get().slug().value()).isEqualTo("default");
        assertThat(tenant.get().status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("GetTenantUseCase.getBySlug resolves default tenant")
    void shouldGetDefaultTenantBySlug() {
        var tenant = getTenantUseCase.getBySlug(TenantSlug.of("default"));

        assertThat(tenant.id()).isEqualTo(DEFAULT_TENANT_ID);
        assertThat(tenant.tenantId()).isEqualTo(DEFAULT_TENANT_ID);
    }
}
