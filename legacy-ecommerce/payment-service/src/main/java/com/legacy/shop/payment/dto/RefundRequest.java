package com.legacy.shop.payment.dto;

import java.math.BigDecimal;

public record RefundRequest(
        Long paymentId,
        BigDecimal amount,
        String reason
) {
}
