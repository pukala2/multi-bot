package com.multibot.shared.exception;

public sealed abstract class DomainException extends RuntimeException
        permits NotFoundException, ForbiddenException, ValidationException, ConflictException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
