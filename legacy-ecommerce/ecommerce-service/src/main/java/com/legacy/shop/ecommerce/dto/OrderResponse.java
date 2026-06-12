package com.legacy.shop.ecommerce.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        List<OrderItemResponse> items,
        double subtotal,
        double discountAmount,
        double tax,
        double totalAmount,
        String status,
        LocalDateTime orderedAt,
        Long paymentId
) {
}
