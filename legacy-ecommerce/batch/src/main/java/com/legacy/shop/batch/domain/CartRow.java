package com.legacy.shop.batch.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * cart 테이블 읽기용 엔티티.
 */
@Entity
@Table(name = "cart")
public class CartRow {

    @Id
    private Long id;

    private Long customerId;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
