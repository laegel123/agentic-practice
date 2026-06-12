package com.legacy.shop.batch.domain;

// 주의: ecommerce-service 의 OrderStatus 와 똑같은 enum 을 또 정의했다 (공유DB라 클래스를 못 가져옴)
public enum OrderStatus {
    CREATED,
    PAID,
    CANCELLED
}
