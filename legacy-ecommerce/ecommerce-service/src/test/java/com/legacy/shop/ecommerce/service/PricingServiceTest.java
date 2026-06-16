package com.legacy.shop.ecommerce.service;

import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.domain.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PricingService.calculate 의 현재 동작 고정.
 * 계산 순서: 소계 → (조건부)할인 → 세금(소계의 10%) → 합계(소계+세금-할인). 모두 MoneyUtils.round(HALF_UP) 경유.
 *
 * 금액 타입 전환(2026-06-16, [ADR-0006]): 금액은 BigDecimal. scale 민감 equals 대신 isEqualByComparingTo 로
 * 값 동등성을 단언한다(값은 종전과 동일, 표현만 BigDecimal).
 */
class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    private OrderItem item(String unitPrice, int qty) {
        OrderItem oi = new OrderItem();
        oi.setUnitPrice(new BigDecimal(unitPrice));
        oi.setQuantity(qty);
        return oi;
    }

    private Coupon coupon(double rate, String minOrderAmount) {
        Coupon c = new Coupon();
        c.setDiscountRate(rate);
        c.setMinOrderAmount(new BigDecimal(minOrderAmount));
        return c;
    }

    @Test
    void noCoupon_subtotalUsesQuantity_taxIsTenPercent() {
        // 소계 = 100 * 2 = 200
        PricingResult r = pricingService.calculate(List.of(item("100.0", 2)), null);

        assertThat(r.subtotal()).isEqualByComparingTo("200.0");
        assertThat(r.discountAmount()).isEqualByComparingTo("0.0");
        assertThat(r.tax()).isEqualByComparingTo("20.0");     // 200 * 0.1
        assertThat(r.total()).isEqualByComparingTo("220.0");  // 200 + 20 - 0
    }

    @Test
    void multipleItems_subtotalIsSumOfLineTotals() {
        // 50*1 + 30*3 = 140
        PricingResult r = pricingService.calculate(List.of(item("50.0", 1), item("30.0", 3)), null);

        assertThat(r.subtotal()).isEqualByComparingTo("140.0");
        assertThat(r.tax()).isEqualByComparingTo("14.0");
        assertThat(r.total()).isEqualByComparingTo("154.0");
    }

    @Test
    void couponApplied_whenSubtotalAtOrAboveMinOrderAmount() {
        // 소계 200, 할인율 10%, 최소주문 100 → 할인 20
        PricingResult r = pricingService.calculate(List.of(item("100.0", 2)), coupon(0.1, "100.0"));

        assertThat(r.subtotal()).isEqualByComparingTo("200.0");
        assertThat(r.discountAmount()).isEqualByComparingTo("20.0");
        assertThat(r.tax()).isEqualByComparingTo("20.0");
        assertThat(r.total()).isEqualByComparingTo("200.0"); // 200 + 20 - 20
    }

    @Test
    void couponIgnored_whenSubtotalBelowMinOrderAmount() {
        // 소계 50 < 최소주문 100 → 할인 미적용
        PricingResult r = pricingService.calculate(List.of(item("50.0", 1)), coupon(0.1, "100.0"));

        assertThat(r.subtotal()).isEqualByComparingTo("50.0");
        assertThat(r.discountAmount()).isEqualByComparingTo("0.0");
        assertThat(r.tax()).isEqualByComparingTo("5.0");
        assertThat(r.total()).isEqualByComparingTo("55.0");
    }
}
