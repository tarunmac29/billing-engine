package com.paycycle.billing_engine.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Saari errors ek jagah handle karo.
 *
 * ============================================================
 * Problem bina GlobalExceptionHandler ke:
 * ============================================================
 * RuntimeException → 500 Internal Server Error
 *   Response: HTML error page ya ugly stack trace
 *   Client ko pata nahi kya galat hua!
 *
 * Solution — @RestControllerAdvice:
 *   Har exception ko catch karo
 *   Clean JSON response return karo
 *   Sahi HTTP status code set karo
 * ============================================================
 *
 * Error response format:
 * {
 *   "timestamp": "2026-05-29T10:00:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Customer not found: abc-123",
 *   "path": "/v1/tenants/.../customers/abc-123"
 * }
 * ============================================================
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ============================================================
    // 404 — Resource nahi mila
    // ============================================================
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(404, "Not Found", ex.getMessage()));
    }

    // ============================================================
    // 409 — Conflict (duplicate email etc.)
    // ============================================================
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "Conflict", ex.getMessage()));
    }

    // ============================================================
    // 400 — Invalid state transition
    // ============================================================
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }

    // ============================================================
    // 400 — Validation errors (@Valid se)
    // ============================================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        // Saare field errors collect karo
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });

        log.warn("Validation failed: {}", fieldErrors);

        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(400)
            .error("Validation Failed")
            .message("Request mein errors hain")
            .fieldErrors(fieldErrors)
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    // ============================================================
    // 401 — Unauthorized
    // ============================================================
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of(401, "Unauthorized", ex.getMessage()));
    }

    // ============================================================
    // 500 — Unexpected errors (last resort)
    // ============================================================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(500, "Internal Server Error",
                "Kuch galat hua. Please try again."));
    }
}
