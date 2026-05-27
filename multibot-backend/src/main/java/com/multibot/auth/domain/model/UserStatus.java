package com.multibot.auth.domain.model;

public enum UserStatus {
    ACTIVE,
    DISABLED,
    MUST_CHANGE_PASSWORD;

    public boolean canLogin() {
        return this == ACTIVE || this == MUST_CHANGE_PASSWORD;
    }
}
