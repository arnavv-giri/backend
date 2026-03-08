package com.thriftbazaar.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central exception handler — maps service-layer exceptions to HTTP responses.
 *
 * Every handler returns the same JSON envelope:
 *
 *   {
 *     "status":    404,
 *     "error":     "Not Found",
 *     "message":   "Product not found with id: 42",
 *     "path":      "/products/42",
 *     "timestamp": "2024-01-15T10:30:00.123"
 *   }
 *
 * Logging strategy
 * ────────────────
 * • Business exceptions (4xx) are logged at WARN — they indicate a bad
 *   client request, not a system fault.  The path and message are included
 *   so the log is actionable without exposing anything sensitive.
 *
 * • Unexpected exceptions (5xx) are logged at ERROR with the full stack
 *   trace so engineers can diagnose them, but the response body sent to
 *   the client contains only a generic message — no internal detail leaks.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 404 ──────────────────────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("404 Not Found — {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ── 403 ──────────────────────────────────────────────────────────────
    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            UnauthorizedActionException ex, HttpServletRequest request) {
        log.warn("403 Forbidden — {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // ── 409 ──────────────────────────────────────────────────────────────
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("409 Conflict — {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ── 400 ──────────────────────────────────────────────────────────────
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            InvalidRequestException ex, HttpServletRequest request) {
        log.warn("400 Bad Request — {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ── 502 — upstream payment gateway failure ──────────────────────────
    @ExceptionHandler(PaymentServiceException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentServiceError(
            PaymentServiceException ex, HttpServletRequest request) {
        log.error("502 Payment Gateway Error — {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex.getCause());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    // ── 500 (safety net) ─────────────────────────────────────────────────
    /**
     * Catches any exception not handled by the specific handlers above.
     *
     * The full stack trace is logged at ERROR level so engineers can
     * diagnose the problem, but the client receives only a generic
     * "An unexpected error occurred" message — no internal detail leaks.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("500 Internal Server Error — {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // ── Helper ───────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        body.put("path",      request.getRequestURI());
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
