package com.legacy.shop.admin.dto;

public record RefundCommand(
        Long paymentId,
        double amount,
        String reason
) {
}
