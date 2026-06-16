package com.legacy.shop.ecommerce.dto;

import java.math.BigDecimal;

public record CreateProductRequest(
        String name,
        BigDecimal price,
        Long categoryId,
        String description,
        int initialStock
) {
}
