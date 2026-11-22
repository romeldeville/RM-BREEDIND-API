package com.rmbreeding.api.config;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Validation failed", request);
        problem.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Validation failed", request);
        problem.setProperty("violations", ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "Malformed request body", request));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage(), request));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessRuleException ex, WebRequest request) {
        HttpStatus status = switch (ex.getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, IDEMPOTENCY_KEY_REUSED -> HttpStatus.CONFLICT;
            case UNAUTHORIZED, TENANT_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, QUOTA_EXCEEDED -> HttpStatus.FORBIDDEN;
            case LOCKED -> HttpStatus.LOCKED;
            case EXPIRED -> HttpStatus.GONE;
            case INVALID_CREDENTIALS, MFA_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status)
                .body(problem(status, ex.getCode(), ex.getMessage(), request));
    }

    @ExceptionHandler(com.rmbreeding.api.shared.MfaRequiredException.class)
    public ResponseEntity<ProblemDetail> handleMfaRequired(com.rmbreeding.api.shared.MfaRequiredException ex,
            WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNAUTHORIZED, ErrorCode.MFA_REQUIRED, ex.getMessage(), request);
        problem.setProperty("challengeId", ex.getChallengeId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied", request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                        "The resource was modified concurrently", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, WebRequest request) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred", request));
    }

    private ProblemDetail problem(HttpStatus status, ErrorCode code, String message, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://api.rmbreeding.com/errors/" + code.name().toLowerCase()));
        problem.setProperty("code", code.name());
        String traceId = request.getHeader("X-Request-Id");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
