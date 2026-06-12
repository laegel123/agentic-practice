package com.legacy.shop.payment.dto;

public record RefundRequest(
        Long paymentId,
        double amount,
        String reason
) {
}
