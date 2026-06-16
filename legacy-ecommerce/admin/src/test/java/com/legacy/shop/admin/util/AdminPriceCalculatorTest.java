package com.legacy.shop.admin.util;

import com.legacy.shop.common.util.MoneyUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdminPriceCalculator 의 금액 산식을 고정한다.
 *
 * R6(중복 제거) 이후 이 계산기는 자체 Math.floor 버림 대신 공용 MoneyUtils(반올림)를 거친다.
 * 따라서 B3(round 가 HALF_UP 로 바뀜) 이후 ecommerce 의 PricingService 와 결과가 일치한다.
 * 금액 타입 전환(2026-06-16, [ADR-0006]): 입출력이 BigDecimal. 값 동등성(isEqualByComparingTo)으로 단언한다.
 * 이 테스트는 (1) HALF_UP 반올림이 적용됨(이전 버림 동작과 다름)과 (2) PricingService 와
 * 동일한 세금/합계 규칙을 공유함을 박제한다.
 */
class AdminPriceCalculatorTest {

    private final AdminPriceCalculator calculator = new AdminPriceCalculator();

    @Test
    void calcTotal_roundsHalfUp_notFloor() {
        // subtotal 99.99 → tax = round(9.999) = 10.00 (이전 floor 였으면 9.99),
        // total = round(99.99 + 10.00) = 109.99 (이전 floor 였으면 109.98)
        assertThat(calculator.calcTotal(new BigDecimal("99.99"), BigDecimal.ZERO))
                .isEqualByComparingTo("109.99");
    }

    @Test
    void calcTotal_appliesTaxAndDiscount() {
        // subtotal 100.00 → tax 10.00, 할인 5.00 → total = 100 + 10 - 5 = 105.00
        assertThat(calculator.calcTotal(new BigDecimal("100.00"), new BigDecimal("5.00")))
                .isEqualByComparingTo("105.00");
    }

    @Test
    void calcTotal_matchesSharedMoneyUtilsRule() {
        BigDecimal subtotal = new BigDecimal("1234.56");
        BigDecimal discount = new BigDecimal("78.90");
        BigDecimal expected = MoneyUtils.round(subtotal.add(MoneyUtils.taxOf(subtotal)).subtract(discount));
        assertThat(calculator.calcTotal(subtotal, discount)).isEqualByComparingTo(expected);
    }
}
