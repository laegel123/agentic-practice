package com.legacy.shop.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 금액 계산 유틸. 시스템 전반에서 금액을 {@link BigDecimal} 로 다룬다([ADR-0006]).
 *
 * <p>정책: 금액은 소수 둘째자리(scale 2)·{@link RoundingMode#HALF_UP}. 반올림 경계는 종전과 동일하다
 * (곱셈은 반올림 없는 정확한 곱, 세금/할인/합계만 {@link #round} 경유). 비율(할인율)은 *금액*이 아니라
 * 무차원 계수라 {@code double} 로 받고, 곱셈 지점에서만 {@code BigDecimal.valueOf(rate)} 로 무손실 변환한다.
 */
public class MoneyUtils {

    /**
     * 금액 정책의 단일 출처(single source of truth). 엔티티 컬럼은 이 상수를 그대로 참조한다
     * (`@Column(precision = MoneyUtils.MONEY_PRECISION, scale = MoneyUtils.MONEY_SCALE)`) — scale 이
     * 코드/스키마/포맷 세 곳에서 따로 박혀 조용히 어긋나는 drift 를 막는다. 한 곳(여기)만 바꾸면 전부 따라온다.
     */
    public static final int MONEY_SCALE = 2;
    public static final int MONEY_PRECISION = 19;

    // 부가세율 10% (계산 핵심 상수라 BigDecimal 로 정밀 고정)
    public static final BigDecimal TAX_RATE = new BigDecimal("0.1");

    private MoneyUtils() {
    }

    /** 금액을 소수 둘째자리로 반올림한다(HALF_UP). */
    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal applyTax(BigDecimal amount) {
        return round(amount.add(amount.multiply(TAX_RATE)));
    }

    public static BigDecimal taxOf(BigDecimal amount) {
        return round(amount.multiply(TAX_RATE));
    }

    /** 단가 × 수량. 반올림 없는 정확한 곱(라인 합계의 원천). */
    public static BigDecimal multiply(BigDecimal price, int qty) {
        return price.multiply(BigDecimal.valueOf(qty));
    }

    /** 금액에 할인율(무차원 계수)을 적용한다. rate 는 {@code BigDecimal.valueOf} 로 무손실 변환한다. */
    public static BigDecimal discount(BigDecimal amount, double rate) {
        return round(amount.multiply(BigDecimal.valueOf(rate)));
    }

    /**
     * 금액을 소수 둘째자리 평문 문자열로 만든다(예: {@code "100.00"}). {@code String.format("%.2f", …)} 는
     * default locale 의존이라 {@code de_DE} 등에서 {@code "100,00"} 을 내므로 쓰지 않는다.
     */
    public static String format(BigDecimal amount) {
        return round(amount).toPlainString();
    }
}
