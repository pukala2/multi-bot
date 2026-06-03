package com.multibot.billing.domain.port.in;

import com.multibot.billing.domain.model.AiCreditSummary;
import com.multibot.billing.domain.model.AiProvider;

import java.math.BigDecimal;
import java.util.UUID;

public interface ManageAiCreditUseCase {

    /** Stan portfela per dostawca + totale + lista wpisów. */
    AiCreditSummary summary();

    /** Dodaje wpis doładowania dla dostawcy (amount może być ujemny = korekta). Zwraca zaktualizowany stan. */
    AiCreditSummary addTopup(AiProvider provider, BigDecimal amountUsd, String note, UUID createdBy);
}
