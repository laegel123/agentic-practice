package com.legacy.shop.ecommerce.repository;

import com.legacy.shop.ecommerce.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductSearchDao.searchByName 의 현재 동작 고정 + SQL 인젝션(E1) 회귀 방지.
 *
 * native 쿼리에 검색어를 문자열로 직접 이어붙이는 구조라(`... name LIKE '%" + keyword + "%'`)
 * 검색어로 SQL 조각을 주입할 수 있다. 아래 테스트는 (1) 정상 검색은 active=TRUE + 이름 LIKE 매칭만
 * 돌려주는 동작을 박제하고(파라미터 바인딩으로 고쳐도 동일 — 동작보존), (2) 인젝션 페이로드가
 * 필터를 우회하는 현재 동작을 고정한다. E1 을 파라미터 바인딩으로 고치면 (2) 의 단언을 같은 커밋에서
 * "리터럴로 처리되어 우회 실패" 로 뒤집는다. (docs/known-issues.md E1)
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(ProductSearchDao.class)
class ProductSearchDaoTest {

    @Autowired
    private ProductSearchDao dao;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void seed() {
        persist("Red Apple", true);
        persist("Green Apple", true);
        persist("SECRET_INACTIVE", false);   // 비활성 — 정상 검색으로는 절대 노출되면 안 된다
        em.flush();
        em.clear();
    }

    private void persist(String name, boolean active) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(1000.0);
        p.setCategoryId(1L);
        p.setDescription("d");
        p.setActive(active);
        em.persist(p);
    }

    @Test
    void legitimateSearch_returnsOnlyActiveMatches() {
        List<Product> result = dao.searchByName("Apple");

        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("Red Apple", "Green Apple");
    }

    @Test
    void legitimateSearch_noMatch_returnsEmpty() {
        assertThat(dao.searchByName("Banana")).isEmpty();
    }

    /**
     * E1 수정(파라미터 바인딩) 후 동작: `' OR 1=1 --` 페이로드는 SQL 조각이 아니라 LIKE 검색값
     * 리터럴로만 취급된다. 그런 이름의 상품이 없으니 결과가 비고, active 필터도 그대로 적용되어
     * 비활성 상품(SECRET_INACTIVE)이 절대 노출되지 않는다 = 인젝션 차단.
     * (수정 전에는 이 페이로드가 필터를 우회해 SECRET_INACTIVE 까지 반환했다.)
     */
    @Test
    void injectionPayload_isTreatedAsLiteral_returnsNothing() {
        List<Product> result = dao.searchByName("' OR 1=1 --");

        assertThat(result).isEmpty();
        assertThat(result).extracting(Product::getName).doesNotContain("SECRET_INACTIVE");
    }
}
