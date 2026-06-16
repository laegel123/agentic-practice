package com.legacy.shop.payment.dto;

import java.math.BigDecimal;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        BigDecimal amount,
        String paymentStatus
) {
}
