package com.legacy.shop.ecommerce.dto;

import java.util.List;

public record CartResponse(
        Long id,
        Long customerId,
        List<CartItemResponse> items,
        double total
) {
}
