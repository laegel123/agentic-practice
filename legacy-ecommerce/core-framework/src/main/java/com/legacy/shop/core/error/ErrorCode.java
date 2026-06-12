package com.legacy.shop.core.error;

/**
 * 전사 공통 에러코드. (모듈 구분 없이 하나의 enum 에 다 몰아넣었다)
 */
public enum ErrorCode {

    // 공통
    INTERNAL_ERROR(500, "C001", "서버 오류가 발생했습니다"),
    INVALID_INPUT(400, "C002", "잘못된 입력입니다"),
    NOT_FOUND(404, "C003", "리소스를 찾을 수 없습니다"),

    // 상품/재고
    PRODUCT_NOT_FOUND(404, "P001", "상품을 찾을 수 없습니다"),
    OUT_OF_STOCK(400, "P002", "재고가 부족합니다"),

    // 주문
    ORDER_NOT_FOUND(404, "O001", "주문을 찾을 수 없습니다"),
    ORDER_ALREADY_CANCELLED(400, "O002", "이미 취소된 주문입니다"),
    EMPTY_CART(400, "O003", "장바구니가 비어있습니다"),

    // 쿠폰
    COUPON_NOT_FOUND(404, "CP001", "쿠폰을 찾을 수 없습니다"),
    COUPON_EXPIRED(400, "CP002", "만료된 쿠폰입니다"),

    // 결제
    PAYMENT_FAILED(400, "PM001", "결제에 실패했습니다"),
    REFUND_EXCEEDS_PAYMENT(400, "PM002", "환불금액이 결제금액을 초과합니다"),
    PAYMENT_NOT_FOUND(404, "PM003", "결제내역을 찾을 수 없습니다"),

    // 고객/권한
    CUSTOMER_NOT_FOUND(404, "U001", "고객을 찾을 수 없습니다"),
    UNAUTHORIZED(401, "U002", "권한이 없습니다");

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
