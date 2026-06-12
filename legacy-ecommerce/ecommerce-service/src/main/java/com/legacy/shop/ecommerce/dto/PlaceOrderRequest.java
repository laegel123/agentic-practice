package com.legacy.shop.ecommerce.dto;

public record PlaceOrderRequest(
        Long customerId,
        String couponCode
) {
}
