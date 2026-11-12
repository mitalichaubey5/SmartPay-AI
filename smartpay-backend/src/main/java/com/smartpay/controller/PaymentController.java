package com.smartpay.controller;

import com.smartpay.dto.response.PaymentResponse;
import com.smartpay.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Payment audit endpoints (read-only; payments are created by the PayPal flow).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentResponse> list(@RequestParam(required = false) Long invoiceId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String method) {
        return paymentService.list(invoiceId, status, method);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return paymentService.get(id);
    }
}
