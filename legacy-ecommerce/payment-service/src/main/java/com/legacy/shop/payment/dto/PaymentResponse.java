package com.legacy.shop.payment.dto;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        Long customerId,
        double amount,
        String status,
        String method
) {
}
