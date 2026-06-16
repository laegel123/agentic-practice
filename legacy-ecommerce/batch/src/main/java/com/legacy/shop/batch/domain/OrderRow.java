package com.legacy.shop.batch.domain;

import com.legacy.shop.common.util.MoneyUtils;
import com.legacy.shop.core.domain.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * orders 테이블 읽기용 엔티티. (ecommerce 의 Order 를 일부 컬럼만 매핑)
 */
@Entity
@Table(name = "orders")
public class OrderRow {

    @Id
    private Long id;

    private Long customerId;

    @Column(precision = MoneyUtils.MONEY_PRECISION, scale = MoneyUtils.MONEY_SCALE)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime orderedAt;

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }
}
