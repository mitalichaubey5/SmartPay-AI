package com.smartpay.entity;

/**
 * Lifecycle of an invoice inside SmartPay.
 */
public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE
}
