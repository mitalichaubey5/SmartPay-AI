package com.smartpay.dto.response;

import java.math.BigDecimal;

/**
 * One line item inside an AI draft.
 */
public record DraftItem(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice) {
}
