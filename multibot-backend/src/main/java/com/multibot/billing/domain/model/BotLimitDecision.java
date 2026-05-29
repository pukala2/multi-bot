package com.multibot.billing.domain.model;

/**
 * Wynik sprawdzenia limitu liczby botow dla organizacji.
 * maxBots = -1 oznacza bez limitu (Enterprise / brak ustawionego limitu).
 */
public record BotLimitDecision(
        boolean allowed,
        int currentBots,
        int maxBots
) {

    public boolean unlimited() {
        return maxBots == -1;
    }
}
