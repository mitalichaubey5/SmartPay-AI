package com.smartpay.dto.response;

import com.smartpay.entity.InvoiceStatus;

/**
 * Result of creating a PayPal order for an invoice. `mode` is "LIVE" when
 * sandbox credentials are configured and "SIMULATED" otherwise — the
 * simulation path is always explicitly labelled.
 */
public record PayPalOrderResponse(
        String orderId,
        String approveUrl,
        String mode,
        InvoiceStatus invoiceStatus) {
}
