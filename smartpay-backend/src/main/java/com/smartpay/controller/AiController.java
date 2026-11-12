package com.smartpay.controller;

import com.smartpay.dto.request.GenerateInvoiceRequest;
import com.smartpay.dto.response.AiInvoiceDraft;
import com.smartpay.service.AiInvoiceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI invoice generation endpoint.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiInvoiceService aiInvoiceService;

    public AiController(AiInvoiceService aiInvoiceService) {
        this.aiInvoiceService = aiInvoiceService;
    }

    @PostMapping("/generate-invoice")
    public AiInvoiceDraft generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return aiInvoiceService.generate(request.prompt());
    }
}
