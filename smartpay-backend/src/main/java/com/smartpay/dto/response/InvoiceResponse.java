package com.smartpay.dto.response;

import com.smartpay.entity.Invoice;
import com.smartpay.entity.InvoiceItem;
import com.smartpay.entity.InvoiceStatus;
import com.smartpay.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Invoice API response (entities never leave the service layer).
 */
public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        String clientName,
        String clientEmail,
        Long customerId,
        String customerName,
        String currency,
        BigDecimal taxRate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        InvoiceStatus status,
        LocalDate dueDate,
        String notes,
        String aiSource,
        List<Item> items,
        PaymentResponse payment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Item(
            Long id,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount) {
    }

    public static InvoiceResponse from(Invoice invoice, Payment latestPayment) {
        List<Item> items = invoice.getItems().stream()
                .map(InvoiceResponse::toItem)
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getClientName(),
                invoice.getClientEmail(),
                invoice.getCustomer() != null ? invoice.getCustomer().getId() : null,
                invoice.getCustomer() != null ? invoice.getCustomer().getName() : null,
                invoice.getCurrency(),
                invoice.getTaxRate(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getTotal(),
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getNotes(),
                invoice.getAiSource(),
                items,
                latestPayment != null ? PaymentResponse.from(latestPayment) : null,
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    private static Item toItem(InvoiceItem item) {
        return new Item(
                item.getId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getAmount());
    }
}
