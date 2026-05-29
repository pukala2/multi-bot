package com.multibot.organization.domain.port.in;

import com.multibot.organization.domain.model.Organization;

public interface CreateOrganizationUseCase {

    /**
     * Tworzy organizacje (klienta). Slug generowany z nazwy (unikalny).
     * planCode musi istniec w billing_plans. companyName + nip opcjonalne (nullable).
     */
    Organization create(String name, String planCode, String companyName, String nip);
}
