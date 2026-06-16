package com.legacy.shop.admin.util;

import com.legacy.shop.common.util.MoneyUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 어드민 화면용 금액 계산기.
 *
 * 세금/합계 산식을 직접 들고 있지 않고 공용 {@link MoneyUtils} 를 거친다 — ecommerce 의
 * PricingService 와 같은 규칙(세금=소계×세율 반올림, 합계=소계+세금-할인 반올림)을 공유한다.
 * (R6) 이전에는 PricingService 계산식을 복붙한 뒤 자체 {@code Math.floor} 로 버림 처리해,
 * MoneyUtils.round 가 HALF_UP 반올림으로 바뀐(B3) 뒤로 미리보기(admin)와 실제 주문(ecommerce)이
 * 같은 금액에도 ±0.01 어긋날 수 있었다. 이제 둘 다 MoneyUtils 를 거치므로 결과가 일치한다.
 * 금액은 {@link BigDecimal} 이다([ADR-0006]).
 */
@Component
public class AdminPriceCalculator {

    public BigDecimal calcTotal(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal tax = MoneyUtils.taxOf(subtotal);
        return MoneyUtils.round(subtotal.add(tax).subtract(discount));
    }
}
