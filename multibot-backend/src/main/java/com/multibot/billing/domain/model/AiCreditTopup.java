package com.multibot.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Pojedynczy wpis doładowania portfela AI. */
public record AiCreditTopup(
        UUID id,
        BigDecimal amountUsd,
        String note,
        Instant createdAt
) {}
