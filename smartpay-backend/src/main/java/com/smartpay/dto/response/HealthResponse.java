package com.smartpay.dto.response;

/**
 * Liveness + connectivity snapshot used by the frontend status bar.
 */
public record HealthResponse(
        String status,
        String database,
        long invoiceCount,
        Ai ai,
        PayPal paypal) {

    public record Ai(String provider, String mode) {
    }

    public record PayPal(String mode) {
    }
}
