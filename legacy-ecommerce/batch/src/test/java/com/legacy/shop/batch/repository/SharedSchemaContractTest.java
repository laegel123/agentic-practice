package com.legacy.shop.batch.repository;

import com.legacy.shop.batch.domain.CartRow;
import com.legacy.shop.batch.domain.InventoryRow;
import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.core.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공유 shop DB 의 batch '읽기 계약' 회귀 테스트([ADR-0008]).
 *
 * <p>인메모리 H2 에 {@code contract/shared-schema.sql}(= ecommerce 가 소유한 테이블 중 batch 가
 * 의존하는 컬럼만 선언한 계약)을 만들고, batch 의 {@code *Row} 프로젝션이 그 컬럼을 올바르게
 * 읽어오는지(매핑 정합)를 박제한다. ecommerce 가 이 컬럼을 옮기거나 타입을 바꾸면(드리프트),
 * (1) 계약 SQL 을 함께 고쳐야 한다는 신호가 되고 (2) 이름이 바뀐 컬럼은 {@code *Row} 의 read 쿼리가
 * 곧장 깨져 조용한 드리프트를 막는다.
 *
 * <p>설정 메모:
 * <ul>
 *   <li>{@code ddl-auto=none} — 스키마 소유자는 ecommerce 다. batch 는 절대 스키마를 만들지 않으며,
 *       이 테스트도 계약 SQL 로만 테이블을 만든다(Hibernate 자동생성 배제 = batch 가 자기 자신이 아니라
 *       '계약'에 대해 검증되도록).</li>
 *   <li>{@code spring.sql.init} — 계약 스키마/시드를 컨텍스트 기동 시 1회 실행한다(@DataJpaTest 가
 *       제공하는 임베디드 H2 는 비-Hikari 라 운영 {@code hikari.read-only=true} 가 적용되지 않아 쓰기 셋업이 된다).</li>
 *   <li>{@link SqlInitializationAutoConfiguration} 을 명시 import 해 슬라이스에서도 SQL 초기화가 도는 걸 보장.</li>
 * </ul>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:contract/shared-schema.sql",
        "spring.sql.init.data-locations=classpath:contract/shared-seed.sql"
})
@ImportAutoConfiguration(SqlInitializationAutoConfiguration.class)
class SharedSchemaContractTest {

    @Autowired
    private OrderRowRepository orderRows;

    @Autowired
    private CartRowRepository cartRows;

    @Autowired
    private InventoryRowRepository inventoryRows;

    @Test
    void orderRow_readsContractColumns() {
        OrderRow o = orderRows.findAll().get(0);

        assertThat(o.getId()).isEqualTo(1L);
        assertThat(o.getCustomerId()).isEqualTo(100L);
        assertThat(o.getTotalAmount()).isEqualByComparingTo("150.00");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(o.getOrderedAt()).isEqualTo(LocalDateTime.of(2026, 6, 16, 9, 0));
    }

    @Test
    void cartRow_readsContractColumns() {
        CartRow c = cartRows.findAll().get(0);

        assertThat(c.getId()).isEqualTo(1L);
        assertThat(c.getCustomerId()).isEqualTo(100L);
        assertThat(c.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 0, 0));
    }

    @Test
    void inventoryRow_readsContractColumns() {
        InventoryRow i = inventoryRows.findAll().get(0);

        assertThat(i.getId()).isEqualTo(1L);
        assertThat(i.getProductId()).isEqualTo(200L);
        assertThat(i.getQuantity()).isEqualTo(-5);
    }
}
