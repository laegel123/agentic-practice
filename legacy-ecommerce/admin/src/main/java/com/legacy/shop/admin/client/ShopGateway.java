package com.legacy.shop.admin.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 이커머스/결제 서비스 호출 게이트웨이. (RestTemplate + Map, URL 하드코딩 기본값)
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
        Map<String, Object> req = new HashMap<>();
        req.put("paymentId", paymentId);
        req.put("amount", amount);
        req.put("reason", reason);
        return restTemplate.postForObject(paymentUrl + "/api/payments/refund", req, Map.class);
    }
}
