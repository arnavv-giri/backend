package com.thriftbazaar.backend.exception;

/**
 * Thrown when incoming request data fails business-rule validation.
 * Example: negative price, empty product name, invalid stock quantity,
 * invalid order status transition, or insufficient stock at checkout.
 * Maps to HTTP 400 in GlobalExceptionHandler.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
