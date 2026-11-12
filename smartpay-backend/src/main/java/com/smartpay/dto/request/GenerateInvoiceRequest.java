package com.smartpay.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI invoice generation request.
 */
public record GenerateInvoiceRequest(

        @NotBlank(message = "prompt is required")
        @Size(min = 10, max = 2000, message = "prompt must be between 10 and 2000 characters")
        String prompt) {
}
