# 정답지 (SPOILER)

> ⚠️ 에이전트에게 노출 금지. 채점자만 본다. 경로는 `legacy-ecommerce/` 기준.
> 결함 ID 는 [seeded-defects.md](seeded-defects.md) 와 매칭된다.

먼저 두 서비스를 띄워두면 대부분 재현된다:
```
./gradlew :payment-service:bootRun     # 8082
./gradlew :ecommerce-service:bootRun    # 8081  (첫 기동 시 시드 데이터 적재)
```
DB 초기화가 필요하면 `~/legacyshopdb.*`, `~/legacypaydb.*` 삭제 후 재기동.

---

## A. 코드 이해 — 기대 답안

- **C1** — 계산은 `PricingService.calculate()` (`ecommerce-service/.../service/PricingService.java`). 순서: 소계 합산 → 할인(쿠폰) → **세금(소계 기준)** → 합계(`소계+세금-할인`). 호출부는 `OrderService.placeOrder()`. 반올림은 `MoneyUtils.round()`. (여기에 D1·D2 버그가 있음)
- **C2** — `OrderService.placeOrder()` 에서 재고 reserve(차감) → 주문 저장 → `PaymentClient.charge()` HTTP 호출 → 실패 시 `catch` 에서 `BusinessException(PAYMENT_FAILED)` throw. 메서드가 `@Transactional` 이라 DB(주문/재고차감)는 롤백됨. 단 외부 결제호출은 보상 없음. (결제 호출을 트랜잭션 안에서 하는 것 자체가 S 악취)
- **C3** — 돈은 전부 `double`. 반올림은 `MoneyUtils.round()` 한 곳에 모여 있으나 `Math.floor` 라 버림(D1). `AdminPriceCalculator` 에도 같은 계산이 복붙됨(S3). 엔티티들의 `price/amount/subtotal/tax/totalAmount/lineTotal` 모두 double(S2).
- **C4** — `batch` 는 ecommerce 와 **같은 H2 파일 DB**(`~/legacyshopdb`)에 붙는다(공유DB, S8). `batch/.../domain/OrderRow`·`InventoryRow`·`CartRow` 가 같은 테이블을 자체 매핑하고 `OrderStatus` enum 도 중복 정의. `ddl-auto:none` 으로 스키마는 ecommerce 가 만든 것을 읽기만 함.

---

## B. 버그 수정 — 원인 / 수정 / 재현

| 과제 | 결함 | 원인 (file · method) | 올바른 수정 | 재현 |
|------|------|----------------------|-------------|------|
| **B1** | D1 | `MoneyUtils.round()` 가 `Math.floor(amount*100)/100` (버림) | `Math.round(...)` 또는 `BigDecimal` `HALF_UP` 로 변경. 근본적으론 `Money` 타입 도입(R1) | 주문 1개(키보드29.99) → total **32.97** (기대 32.98) |
| **B2** | D2 | `PricingService.calculate()` 가 세금을 **소계** 기준으로 매기고 `total=소계+세금-할인` | 세금은 `(소계-할인)` 기준으로: `taxBase=subtotal-discount; tax=round(taxBase*0.1); total=round(taxBase+tax)` | 카트(키보드×2,마우스×1) + `SAVE20` → total **71.97** (기대 ≈70.37) |
| **B3** | D3 | `OrderService.placeOrder()` 가 `reserve()`(차감) 후 결제성공 뒤 `confirm()` 에서 **또 차감** (`InventoryService.confirm()`) | `confirm()` 은 차감하지 말고 확정만(또는 reserve 제거하고 confirm 에서만 차감). 한 번만 차감되게 | 키보드×2 주문 → `/api/products/1/stock` 50→**46** (기대 48) |
| **B4** | D4 | `RefundService.refund()` 에 누적환불≤결제액 검증 없음 (`REFUND_EXCEEDS_PAYMENT` 정의돼 있으나 미사용) | 환불 전 `sum(refunds)+amount > payment.amount` 면 `BusinessException(REFUND_EXCEEDS_PAYMENT)` | 결제 71.97 → refund 60, 60 둘 다 통과 |
| **B5** | D5 | `CouponService.getValidCoupon()` 가 `!expiryDate.isAfter(today)` (당일=만료) | `expiryDate.isBefore(today)` 일 때만 만료로 (당일 포함) | `WELCOME`(만료일=오늘) 주문 시 `COUPON_EXPIRED` |
| **B6** | D6 | `SettlementJob.settle()` 가 상태 무관 전체 합산 | `status==PAID` 만 합산 | 시드 직후 정산 **180.0** (기대 150.0; 취소 30 포함됨) |
| **B7** | D7 | `PageRequestDto.getOffset()` 가 `page*size` (page 는 1-based) | `(page-1)*size` (또는 0-based 규약 통일) | `GET /api/products?page=1&size=5` → 6~10번 상품 (기대 1~5) |
| **B8** | D8 | `AdminRefundController` 에 `adminAuth.check()` 누락 | 다른 어드민 컨트롤러처럼 `X-Admin-Token` 검사 추가 | 토큰 없이 `POST /admin/refunds` 성공 (vs `/admin/products` 401) |
| **B9** | D9 | `CartService.cartTotal()` 가 `+= unitPrice` (수량 누락) | `+= unitPrice * quantity` | 키보드×3 → `GET /api/carts/1` total **29.99** (기대 89.97) |
| **B10** | D11 | `ProductSearchDao.searchByName()` 가 키워드를 SQL에 직접 결합 (`name LIKE '%`+keyword+`%'`) | 파라미터 바인딩(`setParameter`) 또는 Spring Data 쿼리메서드 사용 | `keyword=' OR 1=1 OR name LIKE '` → 전체 12건 (정상 'zzz' 0건). 검증됨 |
| **B11** | D10 | 주문시각 `DateUtils.now()`=UTC, 집계 `LocalDate.now()`=서버로컬 | 저장/조회 타임존 통일(UTC 일관 또는 시스템존 명시) | KST 00:00~09:00 주문이 전날 매출로 집계 (테스트로 시각 고정해 검증) |

---

## C. 기능 추가 — 수용 기준 / 힌트

- **F1 주문취소+환불** — `InventoryService.restore()` 와 `PaymentClient.refund()` 가 이미 존재(미사용)하므로 재사용. 기준: `OrderStatus.PAID` 만 취소 가능 → `CANCELLED` 로 변경, 각 아이템 `restore`, `paymentClient.refund(paymentId, total)`. 이미 `CANCELLED` 면 `ORDER_ALREADY_CANCELLED`. 엔드포인트 예: `POST /api/orders/{id}/cancel`.
- **F2 기프트카드** — `payment-service` 에 결제수단 타입 확장. 잔액 엔티티 + 차감/검증. 잔액 부족 시 실패.
- **F3 리뷰** — `ecommerce-service` 에 `Review`(productId, customerId, rating 1~5, comment) 엔티티/리포지토리/컨트롤러. 평점 범위 검증.

## D. 리팩터링 — 기준

- **R1** — `common-util` 에 불변 `Money`(내부 정수 minor unit 또는 `BigDecimal`) 추가, `MoneyUtils`·`AdminPriceCalculator` 중복 제거, 반올림 일원화. **R1 을 제대로 하면 D1 도 사라짐**.
- **R2** — `placeOrder` 를 `reserveStock / buildOrder / price / pay / complete` 등으로 분해. 동작 동일.
- **R3** — `AdminPriceCalculator` 제거, 공용 계산 재사용 (서비스 분리 시 공유 모듈 또는 HTTP 재사용).

## E. 하네스/에이전틱 — 합격선

- **H1** — `:ecommerce-service:test` 에 주문 플로우 테스트(결제는 stub/mock 또는 `@MockBean`). `./gradlew test` green.
- **H2** — `.github/workflows/ci.yml` 가 `./gradlew build` 수행, PR에서 동작.
- **H3** — 루트 `CLAUDE.md`(빌드/실행/모듈맵/규약) + 모듈별 컨텍스트.
- **H4** — `.claude/settings.json` 에 `./gradlew` 류 allow + 가드레일 훅.
- **H5** — Spotless/Checkstyle/SpotBugs/PMD + Jacoco 적용, 빌드에 연결.
