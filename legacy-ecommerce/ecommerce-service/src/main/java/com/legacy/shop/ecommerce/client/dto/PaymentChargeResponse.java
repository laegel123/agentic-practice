package com.legacy.shop.ecommerce.client.dto;

/**
 * payment charge 응답({@code ApiResponse.data})에서 ecommerce 가 읽는 부분 = 발급된 결제 ID.
 *
 * <p>응답 본문에는 orderId/amount/status 등 다른 필드도 오지만 ecommerce 는 결제 ID 만 필요하므로
 * 나머지는 무시한다(RestTemplate 의 Jackson 컨버터가 알 수 없는 속성을 무시하도록 구성됨).
 */
public record PaymentChargeResponse(Long paymentId) {
}
