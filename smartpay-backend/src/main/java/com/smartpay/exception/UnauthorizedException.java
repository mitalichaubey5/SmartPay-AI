package com.smartpay.exception;

/**
 * Thrown when an endpoint requires authentication (maps to 401).
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
