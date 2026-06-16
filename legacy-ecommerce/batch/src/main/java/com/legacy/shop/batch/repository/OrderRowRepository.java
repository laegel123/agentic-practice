package com.legacy.shop.batch.repository;

import com.legacy.shop.batch.domain.OrderRow;

/**
 * orders 테이블 읽기 전용 리포지토리. 쓰기 메서드는 노출하지 않는다([ADR-0008]).
 */
public interface OrderRowRepository extends ReadOnlyRepository<OrderRow, Long> {
}
