package com.legacy.shop.ecommerce.service;

import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.domain.OrderItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * PricingService.calculate 의 현재 동작 고정.
 * 계산 순서: 소계 → (조건부)할인 → 세금(소계의 10%) → 합계(소계+세금-할인). 모두 MoneyUtils.round(버림) 경유.
 */
class PricingServiceTest {

    private static final double EPS = 1e-6;

    private final PricingService pricingService = new PricingService();

    private OrderItem item(double unitPrice, int qty) {
        OrderItem oi = new OrderItem();
        oi.setUnitPrice(unitPrice);
        oi.setQuantity(qty);
        return oi;
    }

    private Coupon coupon(double rate, double minOrderAmount) {
        Coupon c = new Coupon();
        c.setDiscountRate(rate);
        c.setMinOrderAmount(minOrderAmount);
        return c;
    }

    @Test
    void noCoupon_subtotalUsesQuantity_taxIsTenPercent() {
        // 소계 = 100 * 2 = 200
        PricingResult r = pricingService.calculate(List.of(item(100.0, 2)), null);

        assertThat(r.subtotal()).isCloseTo(200.0, within(EPS));
        assertThat(r.discountAmount()).isCloseTo(0.0, within(EPS));
        assertThat(r.tax()).isCloseTo(20.0, within(EPS));     // 200 * 0.1
        assertThat(r.total()).isCloseTo(220.0, within(EPS));  // 200 + 20 - 0
    }

    @Test
    void multipleItems_subtotalIsSumOfLineTotals() {
        // 50*1 + 30*3 = 140
        PricingResult r = pricingService.calculate(List.of(item(50.0, 1), item(30.0, 3)), null);

        assertThat(r.subtotal()).isCloseTo(140.0, within(EPS));
        assertThat(r.tax()).isCloseTo(14.0, within(EPS));
        assertThat(r.total()).isCloseTo(154.0, within(EPS));
    }

    @Test
    void couponApplied_whenSubtotalAtOrAboveMinOrderAmount() {
        // 소계 200, 할인율 10%, 최소주문 100 → 할인 20
        PricingResult r = pricingService.calculate(List.of(item(100.0, 2)), coupon(0.1, 100.0));

        assertThat(r.subtotal()).isCloseTo(200.0, within(EPS));
        assertThat(r.discountAmount()).isCloseTo(20.0, within(EPS));
        assertThat(r.tax()).isCloseTo(20.0, within(EPS));
        assertThat(r.total()).isCloseTo(200.0, within(EPS)); // 200 + 20 - 20
    }

    @Test
    void couponIgnored_whenSubtotalBelowMinOrderAmount() {
        // 소계 50 < 최소주문 100 → 할인 미적용
        PricingResult r = pricingService.calculate(List.of(item(50.0, 1)), coupon(0.1, 100.0));

        assertThat(r.subtotal()).isCloseTo(50.0, within(EPS));
        assertThat(r.discountAmount()).isCloseTo(0.0, within(EPS));
        assertThat(r.tax()).isCloseTo(5.0, within(EPS));
        assertThat(r.total()).isCloseTo(55.0, within(EPS));
    }
}
