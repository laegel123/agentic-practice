package com.legacy.shop.core.web;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리. 모든 컨트롤러 예외를 여기서 받는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleEtc(Exception e) {
        // 그냥 500 으로 내려준다. (로그도 안 남기고 원인도 안 본다)
        return ResponseEntity.status(500)
                .body(ApiResponse.error("C001", "서버 오류가 발생했습니다"));
    }
}
