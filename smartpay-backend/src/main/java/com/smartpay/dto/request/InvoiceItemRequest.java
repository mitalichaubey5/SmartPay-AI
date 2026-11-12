package com.smartpay.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A single invoice line item.
 */
public record InvoiceItemRequest(

        @NotBlank(message = "item description is required")
        @Size(max = 500)
        String description,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.01", message = "quantity must be at least 0.01")
        BigDecimal quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.00", message = "unitPrice cannot be negative")
        BigDecimal unitPrice) {
}
