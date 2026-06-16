package com.legacy.shop.admin.client.dto;

/**
 * payment 서비스 {@code POST /api/payments/refund} 요청 바디(admin 측 — {@code reason} 포함).
 *
 * <p>과거 {@code ShopGateway} 가 raw {@code Map} 으로 만들던 환불 요청을 타입 record 로 드러낸다(R2).
 */
public record PaymentRefundRequest(Long paymentId, double amount, String reason) {
}
