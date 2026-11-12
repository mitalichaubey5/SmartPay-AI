package com.smartpay.dto.response;

import com.smartpay.entity.Payment;
import com.smartpay.entity.PaymentMethod;
import com.smartpay.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment API response.
 */
public record PaymentResponse(
        Long id,
        Long invoiceId,
        String invoiceNumber,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String paypalOrderId,
        String paypalCaptureId,
        String payerEmail,
        String payerName,
        LocalDateTime capturedAt,
        LocalDateTime createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getInvoice().getInvoiceNumber(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaypalOrderId(),
                payment.getPaypalCaptureId(),
                payment.getPayerEmail(),
                payment.getPayerName(),
                payment.getCapturedAt(),
                payment.getCreatedAt());
    }
}
