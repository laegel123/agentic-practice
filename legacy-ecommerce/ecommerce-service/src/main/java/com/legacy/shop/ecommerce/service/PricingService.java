package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.MoneyUtils;
import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주문 금액 계산. 소계 -> 할인 -> 세금 -> 합계 순서로 계산한다.
 */
@Service
public class PricingService {

    public PricingResult calculate(List<OrderItem> items, Coupon coupon) {
        double subtotal = 0;
        for (OrderItem it : items) {
            subtotal += it.getUnitPrice() * it.getQuantity();
        }

        double discount = 0;
        if (coupon != null && subtotal >= coupon.getMinOrderAmount()) {
            discount = MoneyUtils.round(subtotal * coupon.getDiscountRate());
        }

        // 세금은 소계 기준으로 매긴다.
        double tax = MoneyUtils.round(subtotal * MoneyUtils.TAX_RATE);

        // 합계 = 소계 + 세금 - 할인
        double total = MoneyUtils.round(subtotal + tax - discount);

        return new PricingResult(MoneyUtils.round(subtotal), discount, tax, total);
    }
}
