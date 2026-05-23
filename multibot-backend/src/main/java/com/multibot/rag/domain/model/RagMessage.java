package com.multibot.rag.domain.model;

import java.util.Objects;

public record RagMessage(Role role, String content) {

    public RagMessage {
        Objects.requireNonNull(role);
        Objects.requireNonNull(content);
    }

    public enum Role {USER, ASSISTANT}
}
