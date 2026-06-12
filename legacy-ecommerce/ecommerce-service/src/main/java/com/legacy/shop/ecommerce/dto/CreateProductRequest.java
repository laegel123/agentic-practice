package com.legacy.shop.ecommerce.dto;

public record CreateProductRequest(
        String name,
        double price,
        Long categoryId,
        String description,
        int initialStock
) {
}
