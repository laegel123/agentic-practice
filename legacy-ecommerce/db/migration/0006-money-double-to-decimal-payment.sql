-- ADR-0006: 금액 double -> BigDecimal 전환에 따른 물리 컬럼 마이그레이션 (payment 도메인)
-- 대상 DB : ~/legacypaydb  (jdbc:h2:file:~/legacypaydb) — payment-service 가 ddl-auto:update 로 소유.
-- 전제   : Flyway 없음 — 수동 1회 실행(db/migration/README.md 참고). 모든 프로세스 중지 후 실행 권장.
-- 멱등성 : "SET DATA TYPE DECIMAL(19,2)" 라 이미 DECIMAL(19,2) 인 컬럼에 재실행해도 no-op.
-- 효과   : 기존 DOUBLE 값이 scale 2 로 반올림되며 100.0999999... 류 부동소수 노이즈가 정리된다.

ALTER TABLE payment ALTER COLUMN amount SET DATA TYPE DECIMAL(19, 2);

ALTER TABLE refund  ALTER COLUMN amount SET DATA TYPE DECIMAL(19, 2);

ALTER TABLE ledger  ALTER COLUMN amount SET DATA TYPE DECIMAL(19, 2);
