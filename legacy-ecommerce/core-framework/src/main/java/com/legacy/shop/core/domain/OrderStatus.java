package com.legacy.shop.core.domain;

/**
 * 주문 상태. ecommerce(쓰기 소유)·batch(읽기 소비) 모듈이 공유하는 단일 정의.
 *
 * <p>과거엔 ecommerce·batch 가 각자 동일한 enum 을 복제해 드리프트 위험이 있었다(BT2): ecommerce 가
 * 상태를 추가해도 batch 는 모르고, 미지값을 읽으면 Hibernate 예외가 났다. batch 는 부트 앱인
 * ecommerce-service 를 의존할 수 없으므로, 두 모듈이 함께 의존하는 {@code core-framework}(이미
 * {@code ErrorCode}·{@code BaseTimeEntity} 같은 공유 기반 타입을 보유)로 끌어올려 단일 출처로 통일한다.
 *
 * <p>{@code @Enumerated(STRING)} 로 영속되는 이름(CREATED/PAID/CANCELLED)은 그대로라 DB 값은 불변이다(동작 보존).
 */
public enum OrderStatus {
    CREATED,
    PAID,
    CANCELLED
}
