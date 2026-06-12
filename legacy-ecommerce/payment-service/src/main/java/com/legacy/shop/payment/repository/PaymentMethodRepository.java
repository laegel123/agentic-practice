package com.legacy.shop.payment.repository;

import com.legacy.shop.payment.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByCustomerId(Long customerId);
}
