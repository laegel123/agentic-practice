package com.legacy.shop.ecommerce.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 결제 서비스 호출 클라이언트. (RestTemplate + Map 으로 그냥 주고받는다)
 */
@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String paymentBaseUrl;

    public PaymentClient(RestTemplate restTemplate,
                         @Value("${payment.base-url:http://localhost:8082}") String paymentBaseUrl) {
        this.restTemplate = restTemplate;
        this.paymentBaseUrl = paymentBaseUrl;
    }

    @SuppressWarnings("rawtypes")
    public Long charge(Long orderId, Long customerId, double amount) {
        Map<String, Object> req = new HashMap<>();
        req.put("orderId", orderId);
        req.put("customerId", customerId);
        req.put("amount", amount);

        Map resp = restTemplate.postForObject(paymentBaseUrl + "/api/payments/charge", req, Map.class);
        Map data = (Map) resp.get("data");
        Object pid = data.get("paymentId");
        return ((Number) pid).longValue();
    }

    @SuppressWarnings("rawtypes")
    public void refund(Long paymentId, double amount) {
        Map<String, Object> req = new HashMap<>();
        req.put("paymentId", paymentId);
        req.put("amount", amount);
        restTemplate.postForObject(paymentBaseUrl + "/api/payments/refund", req, Map.class);
    }
}
