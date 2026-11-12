package com.smartpay.exception;

/**
 * Thrown for domain rule violations (maps to 400).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
