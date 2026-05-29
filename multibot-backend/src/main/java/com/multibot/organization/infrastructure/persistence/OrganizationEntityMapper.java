package com.multibot.organization.infrastructure.persistence;

import com.multibot.organization.domain.model.Organization;
import com.multibot.organization.domain.model.OrganizationSlug;
import com.multibot.organization.domain.model.OrganizationStatus;
import com.multibot.shared.organization.OrganizationId;

final class OrganizationEntityMapper {

    private OrganizationEntityMapper() {
    }

    static Organization toDomain(OrganizationJpaEntity entity) {
        return new Organization(
                OrganizationId.of(entity.getId()),
                entity.getName(),
                OrganizationSlug.of(entity.getSlug()),
                entity.getPlan(),
                OrganizationStatus.valueOf(entity.getStatus().name()),
                entity.getCompanyName(),
                entity.getNip(),
                entity.getMonthlyMessagesLimitOverride(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static OrganizationJpaEntity toJpaEntity(Organization org) {
        return OrganizationJpaEntity.builder()
                .id(org.id().value())
                .name(org.name())
                .slug(org.slug().value())
                .plan(org.plan())
                .status(OrganizationJpaEntity.StatusValue.valueOf(org.status().name()))
                .companyName(org.companyName())
                .nip(org.nip())
                .monthlyMessagesLimitOverride(org.monthlyMessagesLimitOverride())
                .createdAt(org.createdAt())
                .updatedAt(org.updatedAt())
                .build();
    }
}
