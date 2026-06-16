-- SharedSchemaContractTest 픽스처: 테이블당 1행씩. batch 의 *Row 프로젝션이 계약 컬럼을
-- 올바르게 읽어오는지(매핑 정합) 확인하는 데 쓴다.
INSERT INTO orders (id, customer_id, total_amount, status, ordered_at)
VALUES (1, 100, 150.00, 'PAID', '2026-06-16 09:00:00');

INSERT INTO cart (id, customer_id, created_at)
VALUES (1, 100, '2026-05-01 00:00:00');

INSERT INTO inventory (id, product_id, quantity)
VALUES (1, 200, -5);
