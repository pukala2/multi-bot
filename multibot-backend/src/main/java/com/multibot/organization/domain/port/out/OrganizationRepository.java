package com.multibot.organization.domain.port.out;

import com.multibot.organization.domain.model.Organization;
import com.multibot.organization.domain.model.OrganizationSlug;
import com.multibot.shared.organization.OrganizationId;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    Optional<Organization> findById(OrganizationId id);

    List<Organization> findAll();

    Organization save(Organization organization);

    boolean existsBySlug(OrganizationSlug slug);
}
