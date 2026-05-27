package com.multibot.auth.domain.model;

public enum Role {
    SUPER_ADMIN,
    OWNER,
    ADMIN;

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isTenantRole() {
        return this == OWNER || this == ADMIN;
    }

    public String springAuthority() {
        return "ROLE_" + name();
    }
}
