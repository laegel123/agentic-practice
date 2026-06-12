package com.legacy.shop.batch.repository;

import com.legacy.shop.batch.domain.CartRow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRowRepository extends JpaRepository<CartRow, Long> {
}
