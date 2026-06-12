package com.legacy.shop.ecommerce.dto;

public record ProductResponse(
        Long id,
        String name,
        double price,
        Long categoryId,
        String description
) {
}
