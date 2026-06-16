package com.legacy.shop.ecommerce.client.dto;

/**
 * payment 서비스 {@code POST /api/payments/charge} 요청 바디.
 *
 * <p>payment 의 {@code ChargeRequest} 는 {@code method} 필드도 갖지만 ecommerce 는 그 값을 보내지
 * 않으므로(과거 raw Map 에도 키가 없었다) 여기에도 포함하지 않는다 — 직렬화 결과가 종전과 같다(동작 보존).
 */
public record PaymentChargeRequest(Long orderId, Long customerId, double amount) {
}
