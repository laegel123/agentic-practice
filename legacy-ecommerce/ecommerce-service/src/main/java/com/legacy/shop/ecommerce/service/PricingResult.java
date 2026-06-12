package com.legacy.shop.ecommerce.service;

public record PricingResult(
        double subtotal,
        double discountAmount,
        double tax,
        double total
) {
}
