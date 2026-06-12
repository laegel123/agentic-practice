package com.legacy.shop.ecommerce.repository;

import com.legacy.shop.ecommerce.domain.Order;
import com.legacy.shop.ecommerce.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerId(Long customerId);

    List<Order> findByStatus(OrderStatus status);
}
