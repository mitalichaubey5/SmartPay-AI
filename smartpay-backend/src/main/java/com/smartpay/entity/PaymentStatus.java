package com.smartpay.entity;

/**
 * Lifecycle of a payment attempt.
 */
public enum PaymentStatus {
    CREATED,
    COMPLETED,
    FAILED,
    REFUNDED
}
