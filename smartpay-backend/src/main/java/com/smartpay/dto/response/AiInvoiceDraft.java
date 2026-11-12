package com.smartpay.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Structured invoice draft produced by the AI layer (Gemini or local
 * fallback parser) — always reviewed/edited by the user before saving.
 */
public record AiInvoiceDraft(
        String clientName,
        String clientEmail,
        String currency,
        BigDecimal taxRate,
        LocalDate dueDate,
        String notes,
        List<DraftItem> items,
        String source,
        String summary) {
}
