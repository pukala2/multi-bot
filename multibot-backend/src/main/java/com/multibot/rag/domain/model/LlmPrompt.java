package com.multibot.rag.domain.model;

import java.util.Objects;

/**
 * Prompt z rozdzieleniem na rolę systemową (zaufane instrukcje) i user (niezaufane dane:
 * kontekst RAG, historia, pytanie). Rozdział ról jest bazowym zabezpieczeniem przed prompt
 * injection — niezaufana treść nigdy nie trafia do roli systemowej.
 */
public record LlmPrompt(String system, String user) {

    public LlmPrompt {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(user, "user");
    }
}
