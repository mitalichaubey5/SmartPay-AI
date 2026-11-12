package com.smartpay.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Create / update an invoice. Totals are always recomputed server-side.
 */
public record CreateInvoiceRequest(

        @NotBlank(message = "clientName is required")
        @Size(max = 200)
        String clientName,

        @Email(message = "clientEmail must be a valid address")
        @Size(max = 200)
        String clientEmail,

        Long customerId,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO-4217 code")
        String currency,

        @DecimalMin(value = "0.00", message = "taxRate cannot be negative")
        @DecimalMax(value = "100.00", message = "taxRate cannot exceed 100")
        BigDecimal taxRate,

        LocalDate dueDate,

        @Size(max = 1000) String notes,

        @Size(max = 40) String aiSource,

        @NotEmpty(message = "invoice must contain at least one item")
        @Valid
        List<InvoiceItemRequest> items) {
}
