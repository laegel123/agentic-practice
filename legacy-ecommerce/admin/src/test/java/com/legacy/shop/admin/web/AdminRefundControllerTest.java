package com.legacy.shop.admin.web;

import com.legacy.shop.admin.client.ShopGateway;
import com.legacy.shop.admin.security.AdminAuth;
import com.legacy.shop.core.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /admin/refunds 의 인증 동작 고정 + 무인증 환불(A1) 회귀 방지.
 *
 * A1 수정 전에는 AdminRefundController 가 AdminAuth 를 주입조차 하지 않아, X-Admin-Token 없이도
 * 누구나 환불을 트리거할 수 있었다(아래 두 테스트가 모두 200 + 게이트웨이 호출로 통과했다).
 * 다른 어드민 컨트롤러와 동일하게 토큰 검사를 추가한 뒤, 이 테스트는 "유효 토큰 없으면 401 이고
 * 다운스트림 환불이 절대 호출되지 않는다" 를 박제한다. (docs/known-issues.md A1)
 *
 * admin 모듈의 첫 테스트다 — 게이트웨이는 HTTP 호출이므로 목으로 대체하고, 실제 AdminAuth 와
 * GlobalExceptionHandler(BusinessException → 상태코드 매핑)를 임포트해 전 구간을 실제로 태운다.
 */
@WebMvcTest(AdminRefundController.class)
@Import({AdminAuth.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "admin.token=test-token")
class AdminRefundControllerTest {

    private static final String BODY = "{\"paymentId\":1,\"amount\":1000.0,\"reason\":\"고객 변심\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopGateway gateway;

    @Test
    void refund_withoutToken_isUnauthorized_andDoesNotTriggerRefund() throws Exception {
        mockMvc.perform(post("/admin/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U002"));

        verify(gateway, never()).refund(any(), anyDouble(), any());
    }

    @Test
    void refund_withWrongToken_isUnauthorized_andDoesNotTriggerRefund() throws Exception {
        mockMvc.perform(post("/admin/refunds")
                        .header("X-Admin-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U002"));

        verify(gateway, never()).refund(any(), anyDouble(), any());
    }

    @Test
    void refund_withValidToken_succeeds_andDelegatesToGateway() throws Exception {
        when(gateway.refund(eq(1L), eq(1000.0), eq("고객 변심")))
                .thenReturn(Map.of("status", "REFUNDED"));

        mockMvc.perform(post("/admin/refunds")
                        .header("X-Admin-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        verify(gateway).refund(eq(1L), eq(1000.0), eq("고객 변심"));
    }
}
