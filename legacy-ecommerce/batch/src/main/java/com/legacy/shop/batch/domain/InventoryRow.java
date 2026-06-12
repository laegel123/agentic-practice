package com.legacy.shop.batch.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * inventory 테이블 읽기용 엔티티.
 */
@Entity
@Table(name = "inventory")
public class InventoryRow {

    @Id
    private Long id;

    private Long productId;

    private int quantity;

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
