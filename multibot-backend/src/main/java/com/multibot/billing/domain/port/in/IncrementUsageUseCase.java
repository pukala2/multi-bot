package com.multibot.billing.domain.port.in;

import com.multibot.shared.tenant.TenantId;

import java.math.BigDecimal;

public interface IncrementUsageUseCase {

    /** Atomowy +1 do user_messages w biezacym okresie. */
    long incrementUserMessage(TenantId tenantId);

    /** Atomowy +1 do ai_messages + dolicz tokeny i koszt CZATU (Anthropic). */
    void incrementAiMessage(TenantId tenantId, long tokensUsed, BigDecimal costUsd);

    /** Dolicz tokeny i koszt EMBEDDINGÓW (OpenAI) — z indeksacji dokumentów. */
    void recordEmbeddingUsage(TenantId tenantId, long tokens, BigDecimal costUsd);
}
