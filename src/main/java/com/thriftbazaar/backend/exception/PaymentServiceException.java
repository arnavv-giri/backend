package com.thriftbazaar.backend.exception;

/**
 * Thrown when an external payment-gateway (Razorpay) call fails.
 * Maps to HTTP 502 Bad Gateway in GlobalExceptionHandler — indicates
 * the upstream payment service is unavailable, not an application bug.
 */
public class PaymentServiceException extends RuntimeException {

    public PaymentServiceException(String message) {
        super(message);
    }

    public PaymentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
