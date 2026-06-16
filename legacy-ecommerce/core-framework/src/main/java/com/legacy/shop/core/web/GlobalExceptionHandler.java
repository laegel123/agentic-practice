package com.legacy.shop.core.web;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리. 모든 컨트롤러 예외를 여기서 받는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleEtc(Exception e) {
        // 비즈니스 외 예외는 500 으로 내려주되, 원인 추적이 가능하도록 스택트레이스를 남긴다 (R4).
        // 응답 자체는 종전과 동일(INTERNAL_ERROR = status 500 / code "C001" / 동일 메시지)하다.
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        log.error("처리되지 않은 예외 — {} 로 응답한다", ec.getCode(), e);
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), ec.getMessage()));
    }
}
