package com.legacy.shop.ecommerce.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
