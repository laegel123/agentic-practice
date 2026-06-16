package com.legacy.shop.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * MoneyUtils 의 동작을 고정하는 테스트.
 *
 * B3 수정(2026-06-16): round() 가 이제 소수 둘째자리 반올림(HALF_UP)으로 동작한다(이전에는
 * Math.floor 로 버림). 아래 단언은 수정된 반올림 동작을 박제한다. (docs/known-issues.md B3)
 */
class MoneyUtilsTest {

    private static final double EPS = 1e-9;

    @Test
    void round_halfUp_thirdDecimal() {
        // 셋째자리 반올림: 1.239 → 1.24 (B3 수정 전 버림이면 1.23).
        assertThat(MoneyUtils.round(1.239)).isCloseTo(1.24, within(EPS));
        // 5.678 → 5.68 (버림이면 5.67).
        assertThat(MoneyUtils.round(5.678)).isCloseTo(5.68, within(EPS));
        // 1.999 → 2.00 (버림이면 1.99).
        assertThat(MoneyUtils.round(1.999)).isCloseTo(2.00, within(EPS));
        // 정확히 떨어지는 값은 그대로.
        assertThat(MoneyUtils.round(2.0)).isCloseTo(2.0, within(EPS));
        // 경계: 0.005 → 0.01 (HALF_UP), 0.004 → 0.00.
        assertThat(MoneyUtils.round(0.005)).isCloseTo(0.01, within(EPS));
        assertThat(MoneyUtils.round(0.004)).isCloseTo(0.00, within(EPS));
    }

    @Test
    void applyTax_addsTenPercent() {
        assertThat(MoneyUtils.applyTax(100.0)).isCloseTo(110.0, within(EPS));
        assertThat(MoneyUtils.applyTax(250.0)).isCloseTo(275.0, within(EPS));
    }

    @Test
    void taxOf_isTenPercent_halfUp() {
        assertThat(MoneyUtils.taxOf(100.0)).isCloseTo(10.0, within(EPS));
        assertThat(MoneyUtils.taxOf(250.0)).isCloseTo(25.0, within(EPS));
        // 99.99 * 0.1 = 9.999 → 반올림이라 10.00 (B3 수정 전 버림이면 9.99).
        assertThat(MoneyUtils.taxOf(99.99)).isCloseTo(10.00, within(1e-6));
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
