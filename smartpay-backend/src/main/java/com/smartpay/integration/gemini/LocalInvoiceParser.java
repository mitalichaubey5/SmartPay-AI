package com.smartpay.integration.gemini;

import com.smartpay.dto.response.AiInvoiceDraft;
import com.smartpay.dto.response.DraftItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline natural-language invoice parser used when no Gemini API key is
 * configured (or when the API call fails). Understands the common invoice
 * phrasings used in the demo: hours/rates, quantities, flat amounts,
 * percentages, due-date offsets, emails and currencies. Drafts produced here
 * are always labelled source = "local-parser" so the UI can show which
 * engine generated them.
 */
@Component
public class LocalInvoiceParser {

    private static final Pattern EMAIL =
            Pattern.compile("([\\w.+-]+@[\\w-]+\\.[\\w.-]+)");

    private static final Pattern TAX_BEFORE =
            Pattern.compile("(?i)(?:tax|vat|gst)[^.%\\d]{0,15}(\\d+(?:\\.\\d+)?)\\s*%");

    private static final Pattern TAX_AFTER =
            Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:tax|vat|gst)");

    private static final Pattern DUE_IN_DAYS =
            Pattern.compile("(?i)(?:due|net)\\s*(?:in\\s*)?(\\d{1,3})\\s*days?");

    private static final Pattern DUE_ISO =
            Pattern.compile("(?i)due\\s*(?:on\\s*)?(\\d{4}-\\d{2}-\\d{2})");

    private static final Pattern NOTES =
            Pattern.compile("(?i)\\bnotes?\\s*[:\\-]\\s*(.+)");

    private static final Pattern CLIENT_INVOICE =
            Pattern.compile("\\b(?i:invoice|bill)\\s+(?i:for|to|of)?\\s*([A-Z][\\w&.\\-']*(?:\\s+[A-Z][\\w&.\\-']*){0,3})");

    private static final Pattern CLIENT_FOR =
            Pattern.compile("\\b(?i:for)\\s+([A-Z][\\w&.\\-']*(?:\\s+[A-Z][\\w&.\\-']*){0,3})");

    /** "10 hours of web development at $75/hour" */
    private static final Pattern HOURS =
            Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?)\\s+(?:of\\s+)?(.+?)\\s+(?:at|@)\\s*[$€£₹]?\\s*(\\d+(?:\\.\\d+)?)");

    /** "$100/hour for 15 hours of consulting" */
    private static final Pattern RATE_FIRST =
            Pattern.compile("(?i)[$€£₹]?(\\d+(?:\\.\\d+)?)\\s*(?:/|per\\s*)\\s*(?:hours?|hrs?|h)\\b.*?(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?)\\s+(?:of\\s+)?(.+)");

    /** "5 units of logo design at $200" */
    private static final Pattern QUANTITY =
            Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:x\\b|units?|pieces?|items?|licenses?|seats?|boxes?|months?|days?)\\s+(?:of\\s+)?(.+?)\\s+(?:at|@)\\s*[$€£₹]?\\s*(\\d+(?:\\.\\d+)?)");

    /** "$500 for website maintenance" or "total of $1200" */
    private static final Pattern FLAT_SYMBOL =
            Pattern.compile("(?i)(?:total\\s+(?:of\\s+)?)?[$€£₹]\\s*(\\d+(?:\\.\\d+)?)\\s*(?:for\\s+)?([a-z][\\w &.\\-]*)?");

    /** "500 for website maintenance" (no currency symbol, requires 'for') */
    private static final Pattern FLAT_FOR =
            Pattern.compile("(?i)(?:total\\s+(?:of\\s+)?)?(\\d+(?:\\.\\d+)?)\\s+for\\s+([a-z][\\w &.\\-]*)");

    public AiInvoiceDraft parse(String prompt) {
        String text = prompt == null ? "" : prompt.trim();
        String clientName = findClientName(text);
        BigDecimal taxRate = findTaxRate(text);
        LocalDate dueDate = findDueDate(text);
        List<DraftItem> items = findItems(text);
        BigDecimal subtotal = items.stream()
                .map(i -> i.quantity().multiply(i.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new AiInvoiceDraft(
                clientName,
                findFirst(EMAIL, text, ""),
                findCurrency(text),
                taxRate,
                dueDate,
                findFirst(NOTES, text, ""),
                items,
                "local-parser",
                summarize(clientName, subtotal, taxRate));
    }

    // ----------------------------------------------------------------- finders

    private List<DraftItem> findItems(String text) {
        List<DraftItem> items = new ArrayList<>();
        for (String segment : text.split("[,;.\\n]")) {
            String chunk = segment.trim();
            if (chunk.isEmpty()) {
                continue;
            }
            Matcher hours = HOURS.matcher(chunk);
            if (hours.find()) {
                items.add(item(clean(hours.group(2)), number(hours.group(1)), number(hours.group(3))));
                continue;
            }
            Matcher rateFirst = RATE_FIRST.matcher(chunk);
            if (rateFirst.find()) {
                items.add(item(clean(rateFirst.group(3)), number(rateFirst.group(2)), number(rateFirst.group(1))));
                continue;
            }
            Matcher quantity = QUANTITY.matcher(chunk);
            if (quantity.find()) {
                items.add(item(clean(quantity.group(2)), number(quantity.group(1)), number(quantity.group(3))));
                continue;
            }
            Matcher flatSymbol = FLAT_SYMBOL.matcher(chunk);
            if (flatSymbol.find()) {
                String description = clean(flatSymbol.group(2));
                items.add(item(description.isBlank() ? "Professional services" : description,
                        BigDecimal.ONE, number(flatSymbol.group(1))));
                continue;
            }
            Matcher flatFor = FLAT_FOR.matcher(chunk);
            if (flatFor.find()) {
                items.add(item(clean(flatFor.group(2)), BigDecimal.ONE, number(flatFor.group(1))));
            }
        }
        if (items.isEmpty()) {
            items.add(item("Professional services", BigDecimal.ONE, BigDecimal.ZERO));
        }
        return items;
    }

    private String findClientName(String text) {
        Matcher matcher = CLIENT_INVOICE.matcher(text);
        if (matcher.find()) {
            return clean(matcher.group(1));
        }
        matcher = CLIENT_FOR.matcher(text);
        if (matcher.find()) {
            return clean(matcher.group(1));
        }
        return "New Client";
    }

    private BigDecimal findTaxRate(String text) {
        Matcher matcher = TAX_BEFORE.matcher(text);
        if (matcher.find()) {
            return number(matcher.group(1));
        }
        matcher = TAX_AFTER.matcher(text);
        if (matcher.find()) {
            return number(matcher.group(1));
        }
        return BigDecimal.ZERO;
    }

    private LocalDate findDueDate(String text) {
        Matcher matcher = DUE_IN_DAYS.matcher(text);
        if (matcher.find()) {
            return LocalDate.now().plusDays(Long.parseLong(matcher.group(1)));
        }
        matcher = DUE_ISO.matcher(text);
        if (matcher.find()) {
            try {
                return LocalDate.parse(matcher.group(1));
            } catch (Exception ignored) {
                // fall through to default
            }
        }
        if (text.toLowerCase().contains("due tomorrow")) {
            return LocalDate.now().plusDays(1);
        }
        return LocalDate.now().plusDays(14);
    }

    private String findCurrency(String text) {
        if (text.contains("$")) {
            return "USD";
        }
        if (text.contains("€")) {
            return "EUR";
        }
        if (text.contains("£")) {
            return "GBP";
        }
        if (text.contains("₹")) {
            return "INR";
        }
        String lower = text.toLowerCase();
        for (String code : List.of("usd", "eur", "gbp", "inr", "aud", "cad", "sgd")) {
            if (lower.contains(code)) {
                return code.toUpperCase();
            }
        }
        return "USD";
    }

    private String findFirst(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? clean(matcher.group(1)) : fallback;
    }

    // ---------------------------------------------------------------- helpers

    private String summarize(String clientName, BigDecimal subtotal, BigDecimal taxRate) {
        return "Invoice for %s — subtotal %s (%s%% tax)".formatted(clientName,
                subtotal.toPlainString(), taxRate.stripTrailingZeros().toPlainString());
    }

    private DraftItem item(String description, BigDecimal quantity, BigDecimal unitPrice) {
        return new DraftItem(description, quantity, unitPrice);
    }

    private BigDecimal number(String value) {
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String clean(String value) {
        String cleaned = value == null ? "" : value.trim();
        cleaned = cleaned.replaceAll("^[$€£₹]+", "").trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (!cleaned.isEmpty() && Character.isLowerCase(cleaned.charAt(0))) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }
        return cleaned;
    }
}
