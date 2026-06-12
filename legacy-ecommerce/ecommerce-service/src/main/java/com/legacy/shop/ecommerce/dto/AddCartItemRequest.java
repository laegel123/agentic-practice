package com.legacy.shop.ecommerce.dto;

public record AddCartItemRequest(
        Long productId,
        int quantity
) {
}
