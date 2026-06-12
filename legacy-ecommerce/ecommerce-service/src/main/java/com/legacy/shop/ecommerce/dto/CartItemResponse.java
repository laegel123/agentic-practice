package com.legacy.shop.ecommerce.dto;

public record CartItemResponse(
        Long productId,
        int quantity,
        double unitPrice,
        double lineTotal
) {
}
