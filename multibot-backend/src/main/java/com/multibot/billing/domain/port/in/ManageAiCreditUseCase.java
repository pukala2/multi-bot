package com.multibot.billing.domain.port.in;

import com.multibot.billing.domain.model.AiCreditSummary;

import java.math.BigDecimal;
import java.util.UUID;

public interface ManageAiCreditUseCase {

    /** Stan portfela: doładowano / wydane / pozostało + lista wpisów. */
    AiCreditSummary summary();

    /** Dodaje wpis doładowania (amount może być ujemny = korekta). Zwraca zaktualizowany stan. */
    AiCreditSummary addTopup(BigDecimal amountUsd, String note, UUID createdBy);
}
