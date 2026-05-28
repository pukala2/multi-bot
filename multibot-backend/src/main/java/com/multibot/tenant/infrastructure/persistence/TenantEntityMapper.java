package com.multibot.tenant.infrastructure.persistence;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantPlan;
import com.multibot.tenant.domain.model.TenantSlug;
import com.multibot.tenant.domain.model.TenantStatus;

final class TenantEntityMapper {

    private TenantEntityMapper() {
    }

    static Tenant toDomain(TenantJpaEntity entity) {
        return new Tenant(
                TenantId.of(entity.getId()),
                entity.getName(),
                TenantSlug.of(entity.getSlug()),
                TenantPlan.valueOf(entity.getPlan().name()),
                TenantStatus.valueOf(entity.getStatus().name()),
                entity.getCompanyName(),
                entity.getNip(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static TenantJpaEntity toJpaEntity(Tenant tenant) {
        return TenantJpaEntity.builder()
                .id(tenant.id().value())
                .name(tenant.name())
                .slug(tenant.slug().value())
                .plan(TenantJpaEntity.TenantPlanValue.valueOf(tenant.plan().name()))
                .status(TenantJpaEntity.TenantStatusValue.valueOf(tenant.status().name()))
                .companyName(tenant.companyName())
                .nip(tenant.nip())
                .createdAt(tenant.createdAt())
                .updatedAt(tenant.updatedAt())
                .build();
    }
}
