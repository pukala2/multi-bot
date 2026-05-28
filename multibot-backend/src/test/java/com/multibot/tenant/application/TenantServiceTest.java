package com.multibot.tenant.application;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.exception.TenantNotFoundException;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantPlan;
import com.multibot.tenant.domain.model.TenantSlug;
import com.multibot.tenant.domain.model.TenantStatus;
import com.multibot.tenant.domain.port.out.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    private TenantRepository repository;
    private TenantService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TenantRepository.class);
        service = new TenantService(repository);
    }

    @Test
    void getById_shouldReturnTenant_whenExists() {
        var tenant = aTenant();
        when(repository.findById(tenant.id())).thenReturn(Optional.of(tenant));

        var result = service.getById(tenant.id());

        assertThat(result).isEqualTo(tenant);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        var id = TenantId.of(UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getBySlug_shouldReturnTenant_whenExists() {
        var tenant = aTenant();
        when(repository.findBySlug(tenant.slug())).thenReturn(Optional.of(tenant));

        var result = service.getBySlug(tenant.slug());

        assertThat(result).isEqualTo(tenant);
    }

    @Test
    void getBySlug_shouldThrow_whenNotFound() {
        var slug = TenantSlug.of("missing");
        when(repository.findBySlug(slug)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySlug(slug))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("missing");
    }

    private Tenant aTenant() {
        var now = Instant.now();
        return new Tenant(
                TenantId.of(UUID.randomUUID()),
                "Acme Corp",
                TenantSlug.of("acme-corp"),
                TenantPlan.FREE,
                TenantStatus.ACTIVE,
                null,
                null,
                now,
                now);
    }
}
