package com.legacy.shop.ecommerce.dto;

public record OrderItemResponse(
        Long productId,
        String productName,
        double unitPrice,
        int quantity,
        double lineTotal
) {
}
