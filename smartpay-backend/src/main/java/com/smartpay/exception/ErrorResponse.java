package com.smartpay.exception;

import java.time.Instant;

/**
 * Uniform REST error envelope.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {
}
