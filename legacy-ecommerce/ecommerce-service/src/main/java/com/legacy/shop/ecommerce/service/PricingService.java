package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.MoneyUtils;
import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 금액 계산. 소계 -> 할인 -> 세금 -> 합계 순서로 계산한다([ADR-0006] BigDecimal).
 */
@Service
public class PricingService {

    public PricingResult calculate(List<OrderItem> items, Coupon coupon) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem it : items) {
            subtotal = subtotal.add(MoneyUtils.multiply(it.getUnitPrice(), it.getQuantity()));
        }

        // 할인·세금은 산출 지점에서 이미 round 된 scale 2 값이다(MoneyUtils.discount/taxOf). 무할인이면
        // round(ZERO)=0.00 으로 두어 두 필드의 scale 을 동일하게 맞춘다(중복 반올림 없이 tax 와 대칭).
        BigDecimal discount = MoneyUtils.round(BigDecimal.ZERO);
        if (coupon != null && subtotal.compareTo(coupon.getMinOrderAmount()) >= 0) {
            discount = MoneyUtils.discount(subtotal, coupon.getDiscountRate());
        }

        // 세금은 소계 기준으로 매긴다.
        BigDecimal tax = MoneyUtils.taxOf(subtotal);

        // 합계 = 소계 + 세금 - 할인
        BigDecimal total = MoneyUtils.round(subtotal.add(tax).subtract(discount));

        // subtotal 만 정확한 곱의 누계라 여기서 round 한다(discount/tax 는 이미 round 됨).
        return new PricingResult(MoneyUtils.round(subtotal), discount, tax, total);
    }
}
