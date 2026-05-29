package com.multibot.config;

import com.multibot.billing.domain.exception.MessageLimitExceededException;
import com.multibot.shared.exception.ConflictException;
import com.multibot.shared.exception.ForbiddenException;
import com.multibot.shared.exception.NotFoundException;
import com.multibot.shared.exception.ValidationException;
import com.multibot.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://multibot.app/errors/";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "FORBIDDEN");
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(ValidationException ex) {
        log.warn("Validation: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Validation Failed", ex.getMessage(), "VALIDATION_FAILED");
    }

    @ExceptionHandler(MessageLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleMessageLimit(MessageLimitExceededException ex) {
        log.warn("Message limit exceeded: tenant used {}/{}",
                ex.decision().currentUsed(), ex.decision().effectiveLimit());
        var pd = baseProblem(HttpStatus.PAYMENT_REQUIRED, "Message Limit Reached",
                "Miesieczny limit wiadomosci zostal osiagniety. Skontaktuj sie z administratorem.",
                "MESSAGE_LIMIT_REACHED");
        pd.setProperty("limit", ex.decision().effectiveLimit());
        pd.setProperty("used", ex.decision().currentUsed());
        pd.setProperty("resetsAt", ex.decision().resetsAt().toString());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(pd);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials");
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Niepoprawny email lub hasło", "BAD_CREDENTIALS");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "CONFLICT");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        var pd = baseProblem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "Request validation failed", "VALIDATION_FAILED");
        pd.setProperty("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", "INTERNAL_ERROR");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, String code) {
        return ResponseEntity.status(status).body(baseProblem(status, title, detail, code));
    }

    private ProblemDetail baseProblem(HttpStatus status, String title, String detail, String code) {
        var pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(ERROR_TYPE_BASE + code.toLowerCase().replace('_', '-')));
        pd.setTitle(title);
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        TenantContext.tryGet().ifPresent(t -> pd.setProperty("tenantId", t.value().toString()));
        return pd;
    }
}
