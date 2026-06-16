package com.legacy.shop.core.web;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 의 응답 매핑을 고정한다.
 *
 * R4 수정(로깅 추가 + 하드코딩 "C001" → ErrorCode.INTERNAL_ERROR)은 **동작 보존**이어야 한다 —
 * 비즈니스 외 예외의 응답은 종전과 동일하게 status 500 / code "C001" / 동일 메시지여야 한다.
 * 이 테스트가 그 계약을 박제해, 향후 로깅/리팩토링이 응답을 바꾸지 않음을 보장한다.
 *
 * 순수 단위 테스트다 — Spring 컨텍스트 없이 핸들러를 직접 호출한다(ResponseEntity 만 검증).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleEtc_returnsInternalError_500_C001_unchanged() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleEtc(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("C001");
        assertThat(body.getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
        assertThat(body.getData()).isNull();
    }

    @Test
    void handleBusiness_mapsErrorCodeStatusAndCode() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleBusiness(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("P001");
    }
}
