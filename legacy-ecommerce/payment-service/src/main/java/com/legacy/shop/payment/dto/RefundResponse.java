package com.legacy.shop.payment.dto;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        double amount,
        String paymentStatus
) {
}
