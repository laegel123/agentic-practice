package com.legacy.shop.ecommerce.repository;

import com.legacy.shop.ecommerce.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
