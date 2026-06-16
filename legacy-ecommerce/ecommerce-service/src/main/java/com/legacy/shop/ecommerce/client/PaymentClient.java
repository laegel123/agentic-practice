package com.legacy.shop.ecommerce.client;

import com.legacy.shop.core.web.ApiResponse;
import com.legacy.shop.ecommerce.client.dto.PaymentChargeRequest;
import com.legacy.shop.ecommerce.client.dto.PaymentChargeResponse;
import com.legacy.shop.ecommerce.client.dto.PaymentRefundRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 결제 서비스 호출 클라이언트.
 *
 * <p>요청·응답을 타입 record 로 주고받는다(R2). 과거엔 raw {@code Map} 으로 직렬화하고
 * {@code ((Number) data.get("paymentId")).longValue()} 처럼 캐스팅해 값을 꺼내, 필드명 오타나
 * 응답 구조 변경이 컴파일 타임에 잡히지 않고 런타임 {@code ClassCastException}/NPE 로 터졌다([ADR-0005]).
 * 이제 계약이 {@link PaymentChargeRequest}/{@link PaymentChargeResponse} 등으로 코드에 드러난다.
 * 호출 URL·요청 바디·읽는 값은 종전과 동일하다(동작 보존).
 */
@Component
public class PaymentClient {

    private static final ParameterizedTypeReference<ApiResponse<PaymentChargeResponse>> CHARGE_RESPONSE =
            new ParameterizedTypeReference<ApiResponse<PaymentChargeResponse>>() {};

    private final RestTemplate restTemplate;
    private final String paymentBaseUrl;

    public PaymentClient(RestTemplate restTemplate,
                         @Value("${payment.base-url:http://localhost:8082}") String paymentBaseUrl) {
        this.restTemplate = restTemplate;
        this.paymentBaseUrl = paymentBaseUrl;
    }

    public Long charge(Long orderId, Long customerId, double amount) {
        PaymentChargeRequest req = new PaymentChargeRequest(orderId, customerId, amount);
        ApiResponse<PaymentChargeResponse> resp = restTemplate.exchange(
                paymentBaseUrl + "/api/payments/charge",
                HttpMethod.POST,
                new HttpEntity<>(req),
                CHARGE_RESPONSE).getBody();
        return resp.getData().paymentId();
    }

    public void refund(Long paymentId, double amount) {
        PaymentRefundRequest req = new PaymentRefundRequest(paymentId, amount);
        // 응답 본문은 ecommerce 에서 사용하지 않는다(환불은 admin→payment 경로가 주력). 읽어서 버린다.
        restTemplate.postForObject(paymentBaseUrl + "/api/payments/refund", req, String.class);
    }
}
