package com.legacy.shop.payment.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        Long customerId,
        BigDecimal amount,
        String status,
        String method
) {
}
