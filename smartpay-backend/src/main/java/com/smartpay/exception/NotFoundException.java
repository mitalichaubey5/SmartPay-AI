package com.smartpay.exception;

/**
 * Thrown when a requested resource does not exist (maps to 404).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
