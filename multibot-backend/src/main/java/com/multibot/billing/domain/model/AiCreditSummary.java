package com.multibot.billing.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stan portfela AI (all-time):
 *   remaining = toppedUp − spent.
 * spent to NASZ szacunek kosztu (cost_usd), nie faktura dostawcy.
 */
public record AiCreditSummary(
        BigDecimal toppedUp,
        BigDecimal spent,
        BigDecimal remaining,
        List<AiCreditTopup> topups
) {}
