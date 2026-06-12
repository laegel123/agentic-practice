package com.legacy.shop.ecommerce.repository;

import com.legacy.shop.ecommerce.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 상품 검색용 레거시 DAO. 네이티브 쿼리로 이름 LIKE 검색을 한다.
 */
@Repository
public class ProductSearchDao {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<Product> searchByName(String keyword) {
        // 검색어를 쿼리 문자열에 그대로 이어붙인다.
        String sql = "SELECT * FROM product WHERE active = TRUE AND name LIKE '%" + keyword + "%'";
        return em.createNativeQuery(sql, Product.class).getResultList();
    }
}
