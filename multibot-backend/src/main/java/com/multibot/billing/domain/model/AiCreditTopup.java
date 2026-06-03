package com.multibot.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Pojedynczy wpis doładowania portfela AI (dla konkretnego dostawcy). */
public record AiCreditTopup(
        UUID id,
        AiProvider provider,
        BigDecimal amountUsd,
        String note,
        Instant createdAt
) {}
