package com.multibot.superadmin.domain.port.in;

import com.multibot.shared.tenant.TenantId;

public interface ProvisionTenantUseCase {

    ProvisionResult provision(ProvisionCommand command);

    record ProvisionCommand(
            String name,
            String ownerEmail,
            String plan,          // FREE / BASIC / PRO / ENTERPRISE
            String companyName,   // nullable
            String nip            // nullable
    ) {
    }

    /** Zwracane RAZ po utworzeniu — generatedPassword pokazujemy super adminowi do przekazania. */
    record ProvisionResult(
            TenantId tenantId,
            String tenantName,
            String ownerEmail,
            String generatedPassword,
            String widgetKey
    ) {
    }
}
