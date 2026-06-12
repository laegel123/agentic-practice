package com.legacy.shop.admin.util;

import org.springframework.stereotype.Component;

/**
 * 어드민 화면용 금액 계산기.
 * (이커머스 PricingService 의 계산 로직을 그대로 복사해 왔다)
 */
@Component
public class AdminPriceCalculator {

    private static final double TAX_RATE = 0.1;

    public double calcTotal(double subtotal, double discount) {
        double tax = Math.floor(subtotal * TAX_RATE * 100) / 100.0;
        return Math.floor((subtotal + tax - discount) * 100) / 100.0;
    }
}
