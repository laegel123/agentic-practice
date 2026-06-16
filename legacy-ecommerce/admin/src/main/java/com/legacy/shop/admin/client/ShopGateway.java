package com.legacy.shop.admin.client;

import com.legacy.shop.admin.client.dto.PaymentRefundRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 이커머스/결제 서비스 호출 게이트웨이.
 *
 * <p>admin 은 자체 도메인 모델이 없는 <b>무상태 프록시</b>다 — 다운스트림 응답을 그대로 재직렬화해
 * 전달한다. 그래서 조회/생성 패스스루의 응답은 의도적으로 {@code Object}/{@code Map} 으로 둔다:
 * 타입을 입히면 admin 이 ecommerce 도메인을 중복 모델링해야 하고, 재직렬화 결과(응답 바이트)도 바뀐다.
 * 반면 admin 이 직접 조립해 보내는 요청 바디(refund)는 타입 record({@link PaymentRefundRequest})로
 * 계약을 드러낸다(R2). 향후 공유 계약 모듈 도입 시 응답까지 타입화한다([ADR-0005]).
 */
@Component
public class ShopGateway {

    private final RestTemplate restTemplate;
    private final String ecommerceUrl;
    private final String paymentUrl;

    public ShopGateway(RestTemplate restTemplate,
                       @Value("${ecommerce.base-url:http://localhost:8081}") String ecommerceUrl,
                       @Value("${payment.base-url:http://localhost:8082}") String paymentUrl) {
        this.restTemplate = restTemplate;
        this.ecommerceUrl = ecommerceUrl;
        this.paymentUrl = paymentUrl;
    }

    public Object listProducts(int page, int size) {
        return restTemplate.getForObject(ecommerceUrl + "/api/products?page=" + page + "&size=" + size, Map.class);
    }

    public Object createProduct(Map<String, Object> body) {
        return restTemplate.postForObject(ecommerceUrl + "/api/products", body, Map.class);
    }

    public Object getOrder(Long id) {
        return restTemplate.getForObject(ecommerceUrl + "/api/orders/" + id, Map.class);
    }

    public Object refund(Long paymentId, double amount, String reason) {
        PaymentRefundRequest req = new PaymentRefundRequest(paymentId, amount, reason);
        return restTemplate.postForObject(paymentUrl + "/api/payments/refund", req, Map.class);
    }
}
