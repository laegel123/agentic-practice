package com.legacy.shop.ecommerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal tax,
        BigDecimal totalAmount,
        String status,
        LocalDateTime orderedAt,
        Long paymentId
) {
}
