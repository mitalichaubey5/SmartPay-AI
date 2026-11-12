package com.smartpay.exception;

/**
 * Thrown when an external integration (Gemini, PayPal) fails (maps to 502).
 */
public class IntegrationException extends RuntimeException {

    public IntegrationException(String message) {
        super(message);
    }

    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
