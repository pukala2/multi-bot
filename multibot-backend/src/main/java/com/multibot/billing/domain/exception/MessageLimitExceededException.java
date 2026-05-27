package com.multibot.billing.domain.exception;

import com.multibot.billing.domain.model.MessageLimitDecision;
import com.multibot.shared.exception.ConflictException;

/**
 * Tenant przekroczyl miesieczny limit wiadomosci wynikajacy z planu.
 * Mapowany na HTTP 402 Payment Required w GlobalExceptionHandler.
 */
public class MessageLimitExceededException extends ConflictException {

    private final MessageLimitDecision decision;

    public MessageLimitExceededException(MessageLimitDecision decision) {
        super("Monthly message limit reached: " + decision.currentUsed() + "/" + decision.effectiveLimit());
        this.decision = decision;
    }

    public MessageLimitDecision decision() {
        return decision;
    }
}
