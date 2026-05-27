package com.multibot.billing.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Wynik sprawdzenia limitu dla danej operacji (nowa wiadomosc usera).
 * <p>
 * effectiveLimit = -1 oznacza unlimited (Enterprise lub override -1).
 */
public record MessageLimitDecision(
        boolean allowed,
        int effectiveLimit,
        long currentUsed,
        LocalDate resetsAt
) {

    public MessageLimitDecision {
        Objects.requireNonNull(resetsAt, "resetsAt");
    }

    public long remaining() {
        if (effectiveLimit == -1) return Long.MAX_VALUE;
        return Math.max(0, effectiveLimit - currentUsed);
    }

    public double percentUsed() {
        if (effectiveLimit == -1 || effectiveLimit == 0) return 0.0;
        return (currentUsed * 100.0) / effectiveLimit;
    }
}
