package com.legacy.shop.admin.util;

import com.legacy.shop.common.util.MoneyUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * AdminPriceCalculator 의 금액 산식을 고정한다.
 *
 * R6(중복 제거) 이후 이 계산기는 자체 Math.floor 버림 대신 공용 MoneyUtils(반올림)를 거친다.
 * 따라서 B3(round 가 HALF_UP 로 바뀜) 이후 ecommerce 의 PricingService 와 결과가 일치한다.
 * 이 테스트는 (1) HALF_UP 반올림이 적용됨(이전 버림 동작과 다름)과 (2) PricingService 와
 * 동일한 세금/합계 규칙을 공유함을 박제한다.
 */
class AdminPriceCalculatorTest {

    private final AdminPriceCalculator calculator = new AdminPriceCalculator();

    @Test
    void calcTotal_roundsHalfUp_notFloor() {
        // subtotal 99.99 → tax = round(9.999) = 10.00 (이전 floor 였으면 9.99),
        // total = round(99.99 + 10.00) = 109.99 (이전 floor 였으면 109.98)
        assertThat(calculator.calcTotal(99.99, 0)).isEqualTo(109.99, within(1e-9));
    }

    @Test
    void calcTotal_appliesTaxAndDiscount() {
        // subtotal 100.00 → tax 10.00, 할인 5.00 → total = 100 + 10 - 5 = 105.00
        assertThat(calculator.calcTotal(100.00, 5.00)).isEqualTo(105.00, within(1e-9));
    }

    @Test
    void calcTotal_matchesSharedMoneyUtilsRule() {
        double subtotal = 1234.56;
        double discount = 78.90;
        double expected = MoneyUtils.round(subtotal + MoneyUtils.taxOf(subtotal) - discount);
        assertThat(calculator.calcTotal(subtotal, discount)).isEqualTo(expected, within(1e-9));
    }
}
