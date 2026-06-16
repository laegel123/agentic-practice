-- ADR-0006: 금액 double -> BigDecimal 전환에 따른 물리 컬럼 마이그레이션 (ecommerce 도메인)
-- 대상 DB : ~/legacyshopdb  (jdbc:h2:file:~/legacyshopdb)
--           ecommerce-service 가 ddl-auto:update 로 소유, batch 가 같은 파일 DB 를 공유(ADR-0002).
--           => orders 의 금액 컬럼은 ecommerce 와 batch(OrderRow.totalAmount) 가 함께 쓰므로 한 번에 옮긴다.
-- 전제   : Flyway 없음 — 수동 1회 실행(db/migration/README.md 참고). 모든 프로세스 중지 후 실행 권장.
-- 멱등성 : "SET DATA TYPE DECIMAL(19,2)" 라 이미 DECIMAL(19,2) 인 컬럼에 재실행해도 no-op.
-- 효과   : 기존 DOUBLE 값이 scale 2 로 반올림되며 100.0999999... 류 부동소수 노이즈가 정리된다.

ALTER TABLE product    ALTER COLUMN price            SET DATA TYPE DECIMAL(19, 2);

ALTER TABLE orders     ALTER COLUMN subtotal         SET DATA TYPE DECIMAL(19, 2);
ALTER TABLE orders     ALTER COLUMN discount_amount  SET DATA TYPE DECIMAL(19, 2);
ALTER TABLE orders     ALTER COLUMN tax              SET DATA TYPE DECIMAL(19, 2);
ALTER TABLE orders     ALTER COLUMN total_amount     SET DATA TYPE DECIMAL(19, 2);

ALTER TABLE order_item ALTER COLUMN unit_price       SET DATA TYPE DECIMAL(19, 2);
ALTER TABLE order_item ALTER COLUMN line_total       SET DATA TYPE DECIMAL(19, 2);

ALTER TABLE cart_item  ALTER COLUMN unit_price       SET DATA TYPE DECIMAL(19, 2);

ALTER TABLE coupon     ALTER COLUMN min_order_amount SET DATA TYPE DECIMAL(19, 2);
-- 주의: coupon.discount_rate 는 그대로 DOUBLE 로 둔다 (할인'율' = 무차원 계수, 금액 아님 — ADR-0006 §1).
