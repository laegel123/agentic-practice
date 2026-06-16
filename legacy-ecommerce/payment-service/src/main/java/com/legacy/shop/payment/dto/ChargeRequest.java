package com.legacy.shop.payment.dto;

import java.math.BigDecimal;

public record ChargeRequest(
        Long orderId,
        Long customerId,
        BigDecimal amount,
        String method
) {
}
