package com.legacy.shop.batch.repository;

import com.legacy.shop.batch.domain.InventoryRow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRowRepository extends JpaRepository<InventoryRow, Long> {
}
