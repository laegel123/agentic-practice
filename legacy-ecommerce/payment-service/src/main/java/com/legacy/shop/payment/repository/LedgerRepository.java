package com.legacy.shop.payment.repository;

import com.legacy.shop.payment.domain.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepository extends JpaRepository<Ledger, Long> {
}
