package com.smartpay.controller;

import com.smartpay.dto.response.InvoiceResponse;
import com.smartpay.dto.response.PayPalOrderResponse;
import com.smartpay.service.PaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayPal payment flow endpoints (create order / capture payment).
 */
@RestController
@RequestMapping("/api/invoices")
public class PayPalController {

    private final PaymentService paymentService;

    public PayPalController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{id}/paypal/order")
    public PayPalOrderResponse createOrder(@PathVariable Long id) {
        return paymentService.createPaymentLink(id);
    }

    @PostMapping("/{id}/paypal/capture")
    public InvoiceResponse capture(@PathVariable Long id) {
        return paymentService.capturePayment(id);
    }
}
