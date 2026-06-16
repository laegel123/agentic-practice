package com.legacy.shop.ecommerce.service;

import java.math.BigDecimal;

public record PricingResult(
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal tax,
        BigDecimal total
) {
}
