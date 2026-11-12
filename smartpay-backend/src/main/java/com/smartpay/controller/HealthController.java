package com.smartpay.controller;

import com.smartpay.config.GeminiProperties;
import com.smartpay.config.PayPalProperties;
import com.smartpay.dto.response.HealthResponse;
import com.smartpay.repository.InvoiceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness + connectivity snapshot.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final InvoiceRepository invoiceRepository;
    private final GeminiProperties geminiProperties;
    private final PayPalProperties payPalProperties;

    public HealthController(JdbcTemplate jdbcTemplate,
                             InvoiceRepository invoiceRepository,
                             GeminiProperties geminiProperties,
                             PayPalProperties payPalProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.invoiceRepository = invoiceRepository;
        this.geminiProperties = geminiProperties;
        this.payPalProperties = payPalProperties;
    }

    @GetMapping
    public HealthResponse health() {
        String database;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            database = "UP";
        } catch (Exception e) {
            database = "DOWN";
        }
        String aiMode = geminiProperties.isConfigured() ? "LIVE" : "FALLBACK";
        String paypalMode = payPalProperties.isConfigured() ? "LIVE" : "SIMULATED";
        return new HealthResponse(
                "UP",
                database,
                invoiceRepository.count(),
                new HealthResponse.Ai("gemini", aiMode),
                new HealthResponse.PayPal(paypalMode));
    }
}
