package com.legacy.shop.admin.client.dto;

import java.math.BigDecimal;

/**
 * payment 서비스 {@code POST /api/payments/refund} 요청 바디(admin 측 — {@code reason} 포함).
 *
 * <p>과거 {@code ShopGateway} 가 raw {@code Map} 으로 만들던 환불 요청을 타입 record 로 드러낸다(R2).
 * {@code amount} 는 BigDecimal 이다([ADR-0006]).
 */
public record PaymentRefundRequest(Long paymentId, BigDecimal amount, String reason) {
}
