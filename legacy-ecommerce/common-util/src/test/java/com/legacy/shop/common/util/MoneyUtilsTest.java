package com.legacy.shop.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MoneyUtils 의 동작을 고정하는 테스트.
 *
 * 금액 타입 전환(2026-06-16, [ADR-0006]): 인자·반환이 {@link BigDecimal} 이다. round() 는 소수
 * 둘째자리 반올림(HALF_UP, B3). scale 민감한 equals 대신 값 동등성(isEqualByComparingTo)을 단언한다.
 * (docs/known-issues.md B3, docs/adr/0006-money-as-bigdecimal.md)
 */
class MoneyUtilsTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void round_halfUp_thirdDecimal() {
        // 셋째자리 반올림: 1.239 → 1.24 (B3 수정 전 버림이면 1.23).
        assertThat(MoneyUtils.round(bd("1.239"))).isEqualByComparingTo("1.24");
        // 5.678 → 5.68 (버림이면 5.67).
        assertThat(MoneyUtils.round(bd("5.678"))).isEqualByComparingTo("5.68");
        // 1.999 → 2.00 (버림이면 1.99).
        assertThat(MoneyUtils.round(bd("1.999"))).isEqualByComparingTo("2.00");
        // 정확히 떨어지는 값은 그대로.
        assertThat(MoneyUtils.round(bd("2.0"))).isEqualByComparingTo("2.00");
        // 경계: 0.005 → 0.01 (HALF_UP), 0.004 → 0.00.
        assertThat(MoneyUtils.round(bd("0.005"))).isEqualByComparingTo("0.01");
        assertThat(MoneyUtils.round(bd("0.004"))).isEqualByComparingTo("0.00");
    }

    @Test
    void round_resultScaleIsTwo() {
        // BigDecimal 전환: 반환 scale 이 2 로 고정된다(DECIMAL(19,2) 컬럼과 일관).
        assertThat(MoneyUtils.round(bd("2"))).hasScaleOf(2);
    }

    @Test
    void applyTax_addsTenPercent() {
        assertThat(MoneyUtils.applyTax(bd("100.0"))).isEqualByComparingTo("110.00");
        assertThat(MoneyUtils.applyTax(bd("250.0"))).isEqualByComparingTo("275.00");
    }

    @Test
    void taxOf_isTenPercent_halfUp() {
        assertThat(MoneyUtils.taxOf(bd("100.0"))).isEqualByComparingTo("10.00");
        assertThat(MoneyUtils.taxOf(bd("250.0"))).isEqualByComparingTo("25.00");
        // 99.99 * 0.1 = 9.999 → 반올림이라 10.00 (B3 수정 전 버림이면 9.99).
        assertThat(MoneyUtils.taxOf(bd("99.99"))).isEqualByComparingTo("10.00");
    }

    @Test
    void discount_isAmountTimesRate() {
        assertThat(MoneyUtils.discount(bd("200.0"), 0.15)).isEqualByComparingTo("30.00");
        assertThat(MoneyUtils.discount(bd("100.0"), 0.1)).isEqualByComparingTo("10.00");
    }

    @Test
    void multiply_isPlainProduct_noRounding() {
        assertThat(MoneyUtils.multiply(bd("10.0"), 3)).isEqualByComparingTo("30.0");
        assertThat(MoneyUtils.multiply(bd("2.5"), 4)).isEqualByComparingTo("10.0");
    }

    @Test
    void taxRate_is10Percent() {
        assertThat(MoneyUtils.TAX_RATE).isEqualByComparingTo("0.1");
    }
}
