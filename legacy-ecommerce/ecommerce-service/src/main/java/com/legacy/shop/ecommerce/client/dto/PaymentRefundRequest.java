package com.legacy.shop.ecommerce.client.dto;

import java.math.BigDecimal;

/**
 * payment 서비스 {@code POST /api/payments/refund} 요청 바디(ecommerce 측 — {@code reason} 없이).
 *
 * <p>admin 의 환불 요청은 {@code reason} 을 포함하지만(별도 record), ecommerce 의 이 경로는 종전대로
 * paymentId/amount 만 보낸다. {@code amount} 는 BigDecimal 이다([ADR-0006]).
 */
public record PaymentRefundRequest(Long paymentId, BigDecimal amount) {
}
