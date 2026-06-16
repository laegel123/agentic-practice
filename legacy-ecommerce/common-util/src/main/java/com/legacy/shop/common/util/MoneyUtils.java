package com.legacy.shop.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 금액 계산 유틸. 시스템 전반에서 금액을 double 로 다룬다.
 */
public class MoneyUtils {

    // 부가세율 10%
    public static final double TAX_RATE = 0.1;

    private MoneyUtils() {
    }

    /**
     * 금액을 소수 둘째자리로 반올림한다(HALF_UP).
     * double 곱셈의 표현 오차를 피하려고 BigDecimal 로 정리한다.
     */
    public static double round(double amount) {
        return BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static double applyTax(double amount) {
        return round(amount + amount * TAX_RATE);
    }

    public static double taxOf(double amount) {
        return round(amount * TAX_RATE);
    }

    public static double multiply(double price, int qty) {
        return price * qty;
    }

    public static double discount(double amount, double rate) {
        return round(amount * rate);
    }

    public static String format(double amount) {
        return String.format("%.2f", amount);
    }
}
