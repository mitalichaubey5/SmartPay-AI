package com.smartpay.dto.response;

import java.time.LocalDateTime;

/**
 * Result of sending an invoice by email.
 *
 * <p>{@code deliveryStatus} is one of:
 * <ul>
 *   <li>{@code SENT} — the SMTP provider accepted the message (never claimed otherwise)</li>
 *   <li>{@code FAILED} — delivery was attempted and rejected (detail in {@code message})</li>
 *   <li>{@code NOT_CONFIGURED} — SMTP credentials are missing; {@code message} explains
 *       exactly which environment variables to set</li>
 * </ul>
 * {@code paypalLinkIncluded} is true only when a genuine PayPal Sandbox
 * checkout link (created for this send) is part of the email.
 */
public record SendInvoiceResponse(
        String deliveryStatus,
        String message,
        String recipient,
        String invoiceNumber,
        boolean paypalLinkIncluded,
        String paypalMode,
        LocalDateTime sentAt) {
}
