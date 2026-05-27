package com.multibot.billing.domain.port.in;

import com.multibot.billing.domain.model.MessageLimitDecision;
import com.multibot.shared.tenant.TenantId;

public interface CheckMessageLimitUseCase {

    /**
     * Sprawdza czy tenant moze wyslac kolejna wiadomosc usera.
     * Zwraca decyzje: allowed = false jak limit przekroczony.
     */
    MessageLimitDecision check(TenantId tenantId);
}
