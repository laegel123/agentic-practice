package com.legacy.shop.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * MoneyUtils 의 현재 동작을 고정하는 characterization 테스트.
 *
 * 주의: round() 는 이름과 달리 Math.floor 로 '버림' 한다(반올림 아님). 아래 단언은 그 현재
 * 동작을 그대로 박제한다 — 향후 반올림으로 수정하면 이 단언들을 같은 커밋에서 뒤집어야 한다.
 * (docs/known-issues.md B3)
 */
class MoneyUtilsTest {

    private static final double EPS = 1e-9;

    @Test
    void round_truncates_thirdDecimal_notHalfUp() {
        // 반올림이면 1.24 가 되어야 하지만, 현재는 버림이라 1.23.
        assertThat(MoneyUtils.round(1.239)).isCloseTo(1.23, within(EPS));
        // 반올림이면 5.68, 현재 버림이라 5.67.
        assertThat(MoneyUtils.round(5.678)).isCloseTo(5.67, within(EPS));
        assertThat(MoneyUtils.round(1.999)).isCloseTo(1.99, within(EPS));
        assertThat(MoneyUtils.round(2.0)).isCloseTo(2.0, within(EPS));
    }

    @Test
    void applyTax_addsTenPercent() {
        assertThat(MoneyUtils.applyTax(100.0)).isCloseTo(110.0, within(EPS));
        assertThat(MoneyUtils.applyTax(250.0)).isCloseTo(275.0, within(EPS));
    }

    @Test
    void taxOf_isTenPercent_truncated() {
        assertThat(MoneyUtils.taxOf(100.0)).isCloseTo(10.0, within(EPS));
        assertThat(MoneyUtils.taxOf(250.0)).isCloseTo(25.0, within(EPS));
        // 99.99 * 0.1 = 9.999 → 버림이라 9.99 (반올림이면 10.00).
        assertThat(MoneyUtils.taxOf(99.99)).isCloseTo(9.99, within(1e-6));
    }

    @Test
    void discount_isAmountTimesRate() {
        assertThat(MoneyUtils.discount(200.0, 0.15)).isCloseTo(30.0, within(EPS));
        assertThat(MoneyUtils.discount(100.0, 0.1)).isCloseTo(10.0, within(EPS));
    }

    @Test
    void multiply_isPlainProduct_noRounding() {
        assertThat(MoneyUtils.multiply(10.0, 3)).isCloseTo(30.0, within(EPS));
        assertThat(MoneyUtils.multiply(2.5, 4)).isCloseTo(10.0, within(EPS));
    }

    @Test
    void taxRate_is10Percent() {
        assertThat(MoneyUtils.TAX_RATE).isCloseTo(0.1, within(EPS));
    }
}
