package com.thriftbazaar.backend.exception;

/**
 * Thrown when an authenticated user attempts an action on a resource they do not own,
 * or when a role requirement is not met at the service layer.
 * Maps to HTTP 403 in GlobalExceptionHandler.
 */
public class UnauthorizedActionException extends RuntimeException {

    public UnauthorizedActionException(String message) {
        super(message);
    }
}
