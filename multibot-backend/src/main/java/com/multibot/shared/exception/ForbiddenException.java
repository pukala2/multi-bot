package com.multibot.shared.exception;

public final class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
