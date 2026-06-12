package com.legacy.shop.payment.dto;

public record ChargeRequest(
        Long orderId,
        Long customerId,
        double amount,
        String method
) {
}
