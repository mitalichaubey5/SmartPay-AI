package com.smartpay.integration.gemini;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.smartpay.dto.response.AiInvoiceDraft;
import com.smartpay.dto.response.DraftItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps the Gemini model's JSON answer onto the {@link AiInvoiceDraft} DTO
 * with defensive sanitization — model output is never trusted blindly.
 */
@Component
public class GeminiInvoiceParser {

    private final ObjectMapper objectMapper;

    public GeminiInvoiceParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiInvoiceDraft parse(String modelText) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripCodeFences(modelText));
        } catch (Exception e) {
            throw new IllegalArgumentException("Gemini answer was not parseable JSON");
        }

        BigDecimal taxRate = clamp(root.path("taxRate").asDouble(0), 0, 100);
        LocalDate dueDate = parseDate(root.path("dueDate").asText(null));
        BigDecimal subtotal = BigDecimal.ZERO;
        java.util.List<DraftItem> items = new java.util.ArrayList<>();
        JsonNode itemsNode = root.path("items");
        if (itemsNode.isArray() && !itemsNode.isEmpty()) {
            for (JsonNode item : itemsNode) {
                String description = sanitizeText(item.path("description").asText("Professional services"));
                BigDecimal quantity = nonNegative(item.path("quantity").asDouble(1));
                BigDecimal unitPrice = nonNegative(item.path("unitPrice").asDouble(0));
                items.add(new DraftItem(description, quantity, unitPrice));
                subtotal = subtotal.add(quantity.multiply(unitPrice));
            }
        } else {
            items.add(new DraftItem("Professional services", BigDecimal.ONE, BigDecimal.ZERO));
        }

        String clientName = sanitizeText(root.path("clientName").asText(""));
        return new AiInvoiceDraft(
                clientName.isBlank() ? "New Client" : clientName,
                sanitizeText(root.path("clientEmail").asText("")),
                normalizeCurrency(root.path("currency").asText("USD")),
                taxRate,
                dueDate,
                sanitizeText(root.path("notes").asText("")),
                items,
                "gemini",
                summarize(clientName, subtotal, taxRate));
    }

    // ------------------------------------------------------------------ helpers

    private String summarize(String clientName, BigDecimal subtotal, BigDecimal taxRate) {
        String who = clientName == null || clientName.isBlank() ? "New Client" : clientName;
        return "Invoice for %s — subtotal %s (%s%% tax)".formatted(who,
                subtotal.setScale(2, java.math.RoundingMode.HALF_UP), taxRate.stripTrailingZeros().toPlainString());
    }

    private String stripCodeFences(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "");
        }
        return cleaned.trim();
    }

    private BigDecimal clamp(double value, double min, double max) {
        double clamped = Math.max(min, Math.min(max, value));
        return BigDecimal.valueOf(clamped).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(double value) {
        return BigDecimal.valueOf(Math.max(0, value)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return LocalDate.now().plusDays(14);
        }
        try {
            return LocalDate.parse(text.trim().substring(0, Math.min(10, text.trim().length())));
        } catch (Exception e) {
            return LocalDate.now().plusDays(14);
        }
    }

    private String normalizeCurrency(String currency) {
        String value = (currency == null || currency.isBlank()) ? "USD" : currency.trim().toUpperCase();
        return value.matches("[A-Z]{3}") ? value : "USD";
    }

    private String sanitizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
