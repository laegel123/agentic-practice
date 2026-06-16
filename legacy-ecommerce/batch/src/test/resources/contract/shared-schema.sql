-- 공유 shop DB 의 batch '읽기 계약'([ADR-0008]).
--
-- ecommerce 가 소유한 테이블(orders/cart/inventory) 중 batch 가 의존하는 컬럼만 선언한다.
-- batch 의 *Row 프로젝션은 이 컬럼들만 매핑하므로, 이 스키마가 곧 두 모듈이 합의한 read 계약이다.
-- SharedSchemaContractTest 가 이 스키마(인메모리 H2)에 대해 batch 의 *Row 읽기를 돌려, ecommerce 가
-- 이 컬럼을 옮기거나 타입을 바꿔 batch 매핑과 어긋나면(드리프트) read 쿼리가 깨지게 한다(조용한 드리프트 차단).
--
-- 컬럼명은 Spring Boot 기본 물리 네이밍(CamelCase→snake_case)을 따른다. 실제 orders/cart/inventory
-- 테이블에는 이 외에도 ecommerce 전용 컬럼(subtotal/tax/created_at 등)이 더 있지만, validate 는
-- '매핑된 컬럼이 존재하는가'만 검사하므로 batch 가 읽는 부분집합만 적으면 된다.

CREATE TABLE orders (
    id           BIGINT        PRIMARY KEY,
    customer_id  BIGINT,
    total_amount DECIMAL(19, 2),
    status       VARCHAR(255),
    ordered_at   TIMESTAMP
);

CREATE TABLE cart (
    id          BIGINT    PRIMARY KEY,
    customer_id BIGINT,
    created_at  TIMESTAMP
);

CREATE TABLE inventory (
    id         BIGINT  PRIMARY KEY,
    product_id BIGINT,
    quantity   INTEGER
);
