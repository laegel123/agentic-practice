package com.legacy.shop.batch.repository;

import com.legacy.shop.batch.domain.OrderRow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRowRepository extends JpaRepository<OrderRow, Long> {
}
