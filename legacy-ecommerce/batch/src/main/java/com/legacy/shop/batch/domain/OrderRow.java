package com.legacy.shop.batch.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    private double totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime orderedAt;

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }
}
