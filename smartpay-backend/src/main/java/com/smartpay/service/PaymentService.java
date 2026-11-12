package com.smartpay.service;

import com.smartpay.dto.response.InvoiceResponse;
import com.smartpay.dto.response.PaymentResponse;
import com.smartpay.dto.response.PayPalOrderResponse;
import com.smartpay.entity.Invoice;
import com.smartpay.entity.InvoiceStatus;
import com.smartpay.entity.Payment;
import com.smartpay.entity.PaymentMethod;
import com.smartpay.entity.PaymentStatus;
import com.smartpay.exception.BadRequestException;
import com.smartpay.integration.paypal.CaptureResult;
import com.smartpay.integration.paypal.PayPalClient;
import com.smartpay.integration.paypal.PayPalOrder;
import com.smartpay.repository.InvoiceRepository;
import com.smartpay.repository.PaymentRepository;
import com.smartpay.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payment orchestration: creates PayPal orders (or a clearly-labelled
 * simulation when sandbox credentials are absent), records every attempt
 * in the payments table, and flips invoices to PAID on successful capture.
 * All invoice access is user-scoped via {@link InvoiceService#findInvoice}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final PayPalClient payPalClient;
    private final CurrentUserProvider currentUserProvider;

    public PaymentService(InvoiceRepository invoiceRepository,
                          PaymentRepository paymentRepository,
                          InvoiceService invoiceService,
                          PayPalClient payPalClient,
                          CurrentUserProvider currentUserProvider) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.payPalClient = payPalClient;
        this.currentUserProvider = currentUserProvider;
    }

    // ---------------------------------------------------------- payment link

    @Transactional
    public PayPalOrderResponse createPaymentLink(Long invoiceId) {
        Invoice invoice = invoiceService.findInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BadRequestException("Invoice " + invoice.getInvoiceNumber() + " is already paid");
        }

        boolean live = payPalClient.isConfigured();
        String orderId;
        String approveUrl;
        if (live) {
            PayPalOrder order = payPalClient.createOrder(
                    invoice.getInvoiceNumber(),
                    "SmartPay invoice " + invoice.getInvoiceNumber(),
                    invoice.getTotal(),
                    invoice.getCurrency());
            orderId = order.orderId();
            approveUrl = order.approveUrl();
        } else {
            orderId = "SIM-" + UUID.randomUUID();
            approveUrl = null;
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setPaypalOrderId(orderId);
        payment.setAmount(invoice.getTotal());
        payment.setCurrency(invoice.getCurrency());
        payment.setMethod(live ? PaymentMethod.PAYPAL : PaymentMethod.SIMULATED);
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);

        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            invoice.setStatus(InvoiceStatus.SENT);
            invoiceRepository.save(invoice);
        }

        log.info("Created {} payment order {} for invoice {}",
                live ? "LIVE" : "SIMULATED", orderId, invoice.getInvoiceNumber());
        return new PayPalOrderResponse(orderId, approveUrl, live ? "LIVE" : "SIMULATED", invoice.getStatus());
    }

    // ---------------------------------------------------------------- capture

    @Transactional
    public InvoiceResponse capturePayment(Long invoiceId) {
        Invoice invoice = invoiceService.findInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BadRequestException("Invoice " + invoice.getInvoiceNumber() + " is already paid");
        }
        Payment payment = paymentRepository
                .findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, PaymentStatus.CREATED)
                .orElseThrow(() -> new BadRequestException(
                        "No payment order exists for this invoice — create a payment link first"));

        if (payPalClient.isConfigured() && payment.getMethod() == PaymentMethod.PAYPAL) {
            CaptureResult result = payPalClient.captureOrder(payment.getPaypalOrderId());
            payment.setPaypalCaptureId(result.captureId());
            payment.setPayerEmail(result.payerEmail());
            payment.setPayerName(result.payerName());
        } else {
            // Clearly-labelled simulation — only reachable without sandbox credentials.
            payment.setPaypalCaptureId("SIM-CAP-" + UUID.randomUUID());
            payment.setPayerEmail("simulation@smartpay.local");
            payment.setPayerName("SmartPay Simulation");
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCapturedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        log.info("Captured payment {} for invoice {}", payment.getPaypalOrderId(), invoice.getInvoiceNumber());
        return invoiceService.toResponse(invoice);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(Long invoiceId, String status, String method) {
        Long userId = currentUserProvider.require().userId();
        List<Payment> payments;
        if (invoiceId != null) {
            // Verifies the invoice belongs to the user first.
            invoiceService.findInvoice(invoiceId);
            payments = paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        } else if (status != null && !status.isBlank()) {
            payments = paymentRepository.findForUserByStatus(userId, parse(PaymentStatus.class, status));
        } else if (method != null && !method.isBlank()) {
            payments = paymentRepository.findForUserByMethod(userId, parse(PaymentMethod.class, method));
        } else {
            payments = paymentRepository.findAllForUser(userId);
        }
        return payments.stream().map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        Long userId = currentUserProvider.require().userId();
        return PaymentResponse.from(paymentRepository.findByIdForUser(id, userId)
                .orElseThrow(() -> new com.smartpay.exception.NotFoundException("Payment " + id + " not found")));
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + type.getSimpleName() + " filter: " + value);
        }
    }
}
