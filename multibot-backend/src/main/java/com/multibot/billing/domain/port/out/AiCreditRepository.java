package com.multibot.billing.domain.port.out;

import com.multibot.billing.domain.model.AiCreditTopup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiCreditRepository {

    void insertTopup(UUID id, BigDecimal amountUsd, String note, UUID createdBy, Instant createdAt);

    /** Suma wszystkich doładowań (all-time). */
    BigDecimal sumTopups();

    /** Wszystkie wpisy, najnowsze pierwsze. */
    List<AiCreditTopup> listTopups();
}
