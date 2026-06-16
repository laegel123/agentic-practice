package com.legacy.shop.ecommerce.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * PaymentClient 의 HTTP 와이어 계약을 고정한다(R2 동작보존 리팩토링의 안전망).
 *
 * <p>타입 record 로 바꾼 뒤에도 (1) charge 요청 바디가 {@code {orderId, customerId, amount}} 그대로이고
 * (method 는 보내지 않음), (2) {@code ApiResponse.data.paymentId} 를 정확히 꺼내며(응답의 나머지 필드는
 * 무시), (3) refund 요청 바디가 {@code {paymentId, amount}} 임을 MockRestServiceServer 로 검증한다.
 */
class PaymentClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PaymentClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new PaymentClient(restTemplate, "http://payment");
    }

    @Test
    void charge_sendsTypedBody_andReturnsPaymentId() {
        server.expect(requestTo("http://payment/api/payments/charge"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.orderId").value(7))
                .andExpect(jsonPath("$.customerId").value(3))
                .andExpect(jsonPath("$.amount").value(220.0))
                .andExpect(jsonPath("$.method").doesNotExist())   // ecommerce 는 method 를 보내지 않는다(동작 보존)
                .andRespond(withSuccess(
                        "{\"code\":\"0000\",\"message\":\"OK\",\"data\":{" +
                                "\"paymentId\":999,\"orderId\":7,\"customerId\":3," +
                                "\"amount\":220.0,\"status\":\"APPROVED\",\"method\":null}}",
                        MediaType.APPLICATION_JSON));

        Long paymentId = client.charge(7L, 3L, new BigDecimal("220.0"));

        assertThat(paymentId).isEqualTo(999L);   // data 의 다른 필드는 무시하고 paymentId 만 읽는다
        server.verify();
    }

    @Test
    void refund_sendsTypedBody() {
        server.expect(requestTo("http://payment/api/payments/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.paymentId").value(5))
                .andExpect(jsonPath("$.amount").value(40.0))
                .andRespond(withSuccess("{\"code\":\"0000\",\"message\":\"OK\",\"data\":null}",
                        MediaType.APPLICATION_JSON));

        client.refund(5L, new BigDecimal("40.0"));

        server.verify();
    }
}
