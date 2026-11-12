package com.smartpay.controller;

import com.smartpay.dto.request.CreateInvoiceRequest;
import com.smartpay.dto.response.InvoiceResponse;
import com.smartpay.dto.response.SendInvoiceResponse;
import com.smartpay.service.InvoiceEmailService;
import com.smartpay.service.InvoicePdfService;
import com.smartpay.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Invoice CRUD endpoints. PayPal payment endpoints live in
 * {@link PayPalController}.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final InvoiceEmailService invoiceEmailService;

    public InvoiceController(InvoiceService invoiceService,
                             InvoicePdfService invoicePdfService,
                             InvoiceEmailService invoiceEmailService) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.invoiceEmailService = invoiceEmailService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceResponse created = invoiceService.create(request);
        return ResponseEntity
                .created(URI.create("/api/invoices/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<InvoiceResponse> list(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String search) {
        return invoiceService.list(status, search);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody CreateInvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    /**
     * Branded PDF download (user-scoped — another user's invoice is 404).
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoicePdfService.PdfResult pdf = invoicePdfService.generateFor(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.fileName() + "\"")
                .body(pdf.content());
    }

    /**
     * Emails the invoice to the client (PDF attached, PayPal checkout link
     * when sandbox credentials exist). Always 200 with an honest
     * deliveryStatus of SENT / FAILED / NOT_CONFIGURED in the body.
     */
    @PostMapping("/{id}/send")
    public SendInvoiceResponse send(@PathVariable Long id) {
        return invoiceEmailService.sendInvoice(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
