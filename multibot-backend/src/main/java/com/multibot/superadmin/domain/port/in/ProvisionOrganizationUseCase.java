package com.multibot.superadmin.domain.port.in;

import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;

public interface ProvisionOrganizationUseCase {

    ProvisionResult provision(ProvisionCommand command);

    record ProvisionCommand(
            String name,
            String ownerEmail,
            String plan,          // FREE / BASIC / PRO / ENTERPRISE
            String companyName,   // nullable
            String nip,           // nullable
            String firstBotName,  // nullable -> domyslnie nazwa organizacji
            String firstBotUrl    // nullable -> adres strony pierwszego bota (allowed_origins)
    ) {
    }

    /** Zwracane RAZ po utworzeniu — generatedPassword pokazujemy super adminowi do przekazania. */
    record ProvisionResult(
            OrganizationId organizationId,
            String organizationName,
            String ownerEmail,
            String generatedPassword,
            TenantId firstBotId,
            String firstBotName,
            String widgetKey
    ) {
    }
}
