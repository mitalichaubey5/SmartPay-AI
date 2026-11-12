package com.smartpay.service;

import com.smartpay.dto.response.AiInvoiceDraft;
import com.smartpay.integration.gemini.GeminiClient;
import com.smartpay.integration.gemini.GeminiInvoiceParser;
import com.smartpay.integration.gemini.LocalInvoiceParser;
import com.smartpay.config.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI invoice generation: Gemini when a GEMINI_API_KEY is configured, with
 * automatic fallback to the built-in local parser so the product keeps
 * working offline. The draft is always labelled with the engine that
 * produced it.
 */
@Service
public class AiInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(AiInvoiceService.class);

    private static final String SYSTEM_INSTRUCTION = """
            You are an invoice extraction engine. Extract a structured invoice from the user's description.
            Return ONLY valid JSON with exactly these keys:
            - clientName: string (the person or company being billed)
            - clientEmail: string (may be empty if unknown)
            - currency: 3-letter ISO-4217 code (USD if unknown)
            - taxRate: number, percentage (0 if not mentioned)
            - dueDate: string YYYY-MM-DD
            - notes: string (may be empty)
            - items: array of objects with description (string), quantity (number), unitPrice (number)
            If no due date is mentioned, use today plus 14 days. Do not invent prices.
            """;

    private final GeminiProperties geminiProperties;
    private final GeminiClient geminiClient;
    private final GeminiInvoiceParser geminiParser;
    private final LocalInvoiceParser localParser;

    public AiInvoiceService(GeminiProperties geminiProperties,
                            GeminiClient geminiClient,
                            GeminiInvoiceParser geminiParser,
                            LocalInvoiceParser localParser) {
        this.geminiProperties = geminiProperties;
        this.geminiClient = geminiClient;
        this.geminiParser = geminiParser;
        this.localParser = localParser;
    }

    public AiInvoiceDraft generate(String prompt) {
        if (geminiProperties.isConfigured()) {
            try {
                String modelText = geminiClient.generateJson(SYSTEM_INSTRUCTION, prompt);
                return geminiParser.parse(modelText);
            } catch (Exception e) {
                log.warn("Gemini generation failed, falling back to local parser: {}", e.getMessage());
            }
        }
        return localParser.parse(prompt);
    }
}
