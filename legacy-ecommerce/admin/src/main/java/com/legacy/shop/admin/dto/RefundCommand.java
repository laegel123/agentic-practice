package com.legacy.shop.admin.dto;

import java.math.BigDecimal;

public record RefundCommand(
        Long paymentId,
        BigDecimal amount,
        String reason
) {
}
