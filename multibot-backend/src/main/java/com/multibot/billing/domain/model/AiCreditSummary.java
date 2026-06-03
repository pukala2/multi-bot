package com.multibot.billing.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stan portfela AI (all-time), z rozbiciem na dostawców.
 * Per dostawca: remaining = toppedUp − spent. Totale = sumy po dostawcach.
 * spent to NASZ szacunek (cost_usd czatu / embedding_cost), nie faktura dostawcy.
 */
public record AiCreditSummary(
        List<ProviderWallet> wallets,
        BigDecimal totalToppedUp,
        BigDecimal totalSpent,
        BigDecimal totalRemaining,
        List<AiCreditTopup> topups
) {

    public record ProviderWallet(
            AiProvider provider,
            BigDecimal toppedUp,
            BigDecimal spent,
            BigDecimal remaining
    ) {}
}
