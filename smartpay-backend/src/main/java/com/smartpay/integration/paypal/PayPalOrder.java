package com.smartpay.integration.paypal;

/**
 * Created PayPal order (id + buyer approval link).
 */
public record PayPalOrder(String orderId, String approveUrl) {
}
