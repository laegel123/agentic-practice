package com.legacy.shop.ecommerce.client.dto;

/**
 * payment 서비스 {@code POST /api/payments/refund} 요청 바디(ecommerce 측 — {@code reason} 없이).
 *
 * <p>admin 의 환불 요청은 {@code reason} 을 포함하지만(별도 record), ecommerce 의 이 경로는 종전대로
 * paymentId/amount 만 보낸다(동작 보존).
 */
public record PaymentRefundRequest(Long paymentId, double amount) {
}
