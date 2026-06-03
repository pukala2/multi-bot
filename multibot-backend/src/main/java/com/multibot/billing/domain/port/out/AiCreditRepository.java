package com.multibot.billing.domain.port.out;

import com.multibot.billing.domain.model.AiCreditTopup;
import com.multibot.billing.domain.model.AiProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public interface AiCreditRepository {

    void insertTopup(UUID id, AiProvider provider, BigDecimal amountUsd, String note, UUID createdBy, Instant createdAt);

    /** Suma doładowań danego dostawcy (all-time). */
    BigDecimal sumTopups(AiProvider provider);

    /** Wszystkie wpisy, najnowsze pierwsze. */
    List<AiCreditTopup> listTopups();
}
