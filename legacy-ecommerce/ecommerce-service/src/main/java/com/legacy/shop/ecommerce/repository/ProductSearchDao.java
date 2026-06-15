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
        // 검색어는 바인딩 파라미터로 전달한다 — SQL 조각이 아니라 LIKE 값으로만 취급되어 인젝션을 막는다.
        String sql = "SELECT * FROM product WHERE active = TRUE AND name LIKE :keyword";
        return em.createNativeQuery(sql, Product.class)
                .setParameter("keyword", "%" + keyword + "%")
                .getResultList();
    }
}
