package com.smartpay.integration.paypal;

/**
 * Result of capturing a PayPal order.
 */
public record CaptureResult(String status, String captureId, String payerEmail, String payerName) {
}
