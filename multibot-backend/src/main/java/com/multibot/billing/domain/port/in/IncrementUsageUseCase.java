package com.multibot.billing.domain.port.in;

import com.multibot.shared.tenant.TenantId;

import java.math.BigDecimal;

public interface IncrementUsageUseCase {

    /** Atomowy +1 do user_messages w biezacym okresie. */
    long incrementUserMessage(TenantId tenantId);

    /** Atomowy +1 do ai_messages + dolicz tokeny i koszt. */
    void incrementAiMessage(TenantId tenantId, long tokensUsed, BigDecimal costUsd);
}
