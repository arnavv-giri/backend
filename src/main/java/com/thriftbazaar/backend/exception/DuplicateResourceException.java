package com.thriftbazaar.backend.exception;

/**
 * Thrown when a resource already exists and a duplicate would be created.
 * Example: registering a vendor profile for a user who already has one,
 * or registering an email that is already taken.
 * Maps to HTTP 409 in GlobalExceptionHandler.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
