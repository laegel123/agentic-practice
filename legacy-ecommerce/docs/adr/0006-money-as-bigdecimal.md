# ADR-0006: 금액을 `BigDecimal` 로 전환

- **상태**: Accepted — [ADR-0003](./0003-money-as-double.md)(금액 `double`)을 대체(Supersedes)
- **날짜**: 2026-06-16
- **결정자**: 하네스 엔지니어링 리팩토링 패스

## 맥락 (Context)

[ADR-0003](./0003-money-as-double.md) 은 모든 금액을 `double` 로 저장·계산하기로 했다. 그 결과
부동소수 표현 오차가 세금/할인/합계 누적에 끼어들 수 있었고, 완화책으로 `MoneyUtils.round()` 한 곳에서
소수 둘째자리 반올림(B3 ✅ HALF_UP)만 모았다. 하지만 **값 자체가 여전히 `double`** 이라 근본 원인(이진
부동소수)은 남아 있었다 — `0.1 + 0.2 != 0.3`, 누계 비교(`refundedTotal >= payment.getAmount()`)의
미세 오차, 직렬화 시 `109.99000000000001` 류 노이즈 위험. [known-issues.md](../known-issues.md) "대형 과제"는
이 근본 해결로 **금액 타입을 `BigDecimal` 로 전환**하는 것을 명시했고, 정산/회계 정합성 요구가 재검토
트리거였다. 이번 패스에서 그 전환을 수행한다.

## 결정 (Decision)

**시스템 전반의 "금액(amount)"을 `double` → `java.math.BigDecimal` 로 전환한다.** 적용 범위와 정책:

### 1. 무엇을 바꾸나 — "금액"만

- **금액(money) = `BigDecimal`**: 상품가·소계·할인액·세금·합계·결제액·환불액·원장액·장바구니 합계.
  엔티티 컬럼, DTO/record 필드, 서비스 시그니처, 서비스 간 HTTP 바디, 배치 집계 누계까지 전부.
- **비율(rate) = `double` 유지**: `Coupon.discountRate`(0.1=10%), `MoneyUtils` 의 할인율 인자.
  비율은 *금액*이 아니라 무차원 계수다. `BigDecimal.valueOf(double)` 이 `Double.toString` 기반이라
  `valueOf(0.2) == "0.2"` 로 **무손실**이므로, 곱셈 지점(`amount.multiply(BigDecimal.valueOf(rate))`)에서만
  변환한다. 이로써 `coupon.discount_rate` **컬럼 타입 변경을 피해** 파급과 마이그레이션 리스크를 줄인다.
  단 세율 상수 `MoneyUtils.TAX_RATE` 는 `new BigDecimal("0.1")` 로 둔다(계산 핵심 상수라 정밀 고정).
- **수량(quantity) = `int` 유지**: 정수 개수.

### 2. 정밀도·반올림 정책 (계산 경계 보존)

- **scale = 2, RoundingMode.HALF_UP** — 기존 `MoneyUtils.round` 정책 그대로.
- **반올림 경계(어디서 round 하나)를 바꾸지 않는다.** `multiply`(단가×수량)는 반올림 없이 정확한 곱,
  `taxOf`/`discount`/`applyTax`/합계는 기존과 동일 지점에서 `round`. 즉 *값*은 종전과 동일하고
  *표현*만 정확해진다(이미 깔끔히 떨어지던 시나리오 값들은 불변).

### 3. DB 컬럼 전략

- 모든 금액 엔티티 컬럼에 **`@Column(precision = 19, scale = 2)`** 를 명시한다.
  ⚠️ Hibernate 의 `BigDecimal` 기본 scale 은 0(정수 절단)이라 **반드시 명시**해야 한다.
- 테스트 프로파일은 인메모리 H2 `ddl-auto: create-drop` 이라 매 실행 엔티티에서 `DECIMAL(19,2)` 스키마를
  새로 만든다 → 즉시 일관.
- ⚠️ **기존 파일 DB 마이그레이션**: 운영 ecommerce DB(`~/legacyshopdb`)는 `ddl-auto: update` 라
  Hibernate 가 기존 `DOUBLE` 컬럼을 `DECIMAL` 로 **바꾸지 않는다**(update 는 타입 변경 미수행). JDBC 가
  `DOUBLE↔BigDecimal` 을 변환하므로 **동작은 하지만** 물리 저장은 여전히 `double` 이라 정밀도 이득은
  컬럼을 실제로 옮겨야 완결된다(물리 `DOUBLE` 을 `getBigDecimal` 로 읽으면 `100.10` 이 `100.0999…`(scale~17)인
  `BigDecimal` 로 올라와, 값 비교는 맞아도 scale 이 더러워진다 — 컬럼 전환이 이 노이즈의 근원을 없앤다).
- **실행 가능한 산출물(멱등 `ALTER TABLE`)을 리포지토리에 커밋했다**:
  [`db/migration/0006-money-double-to-decimal-ecommerce.sql`](../../db/migration/0006-money-double-to-decimal-ecommerce.sql)
  (`~/legacyshopdb` — `product`/`orders`/`order_item`/`cart_item`/`coupon.min_order_amount`)와
  [`db/migration/0006-money-double-to-decimal-payment.sql`](../../db/migration/0006-money-double-to-decimal-payment.sql)
  (`~/legacypaydb` — `payment`/`refund`/`ledger`). 각 문은 `SET DATA TYPE DECIMAL(19,2)` 라 재실행해도
  no-op(멱등)이고, 변환 시 H2 가 기존 값을 scale 2 로 반올림해 위 부동소수 노이즈를 정리한다. 적용 절차는
  [`db/migration/README.md`](../../db/migration/README.md) 참고. ⚠️ Flyway 가 없어 **자동 실행되지 않으며 수동
  1회 실행**이다. [ADR-0002](./0002-shared-h2-file-database.md) 공유 DB 구조라 `orders` 금액 컬럼은
  ecommerce·batch 가 함께 쓰므로(한 컬럼을 옮기면 양쪽에 반영) ecommerce 스크립트 한 벌로 함께 옮겨진다.
- 보존할 데이터가 없는 로컬/테스트라면 마이그레이션 대신 **DB 파일 삭제 후 재생성**(`~/legacyshopdb*.db`,
  `~/legacypaydb*.db` 제거 → ecommerce·payment 재기동 시 `DECIMAL` 로 재생성)이 가장 단순하다. 테스트
  프로파일은 인메모리 `create-drop` 이라 매 실행 새로 만들어 스크립트가 불필요하다.

### 4. 직렬화(wire) 변화는 **수용한다**

- Jackson 은 `BigDecimal` 을 scale 보존해 직렬화한다: `double 100.0` → `BigDecimal "100.00"` 처럼
  **응답 JSON 바이트가 바뀐다**(`100.0` → `100.00`). 서비스 내부 계약(ecommerce↔payment↔admin)은
  양쪽이 함께 전환되어 정합하다. 이 표현 변화는 **타입 전환의 의도된 귀결**이며, 그동안 강조해 온
  "동작 보존"의 **명시적 예외**다(정밀도를 얻기 위한 트레이드오프). 와이어 테스트(`PaymentClientTest`,
  `AdminRefundControllerTest`)의 금액 단언을 그에 맞게 갱신한다.

### 5. 테스트 전환 규약

- characterization 테스트의 `double` 금액 단언을 `BigDecimal` 로 전환하되 **값은 보존**한다.
- AssertJ `isEqualTo(BigDecimal)` 는 scale 민감(`80.0 != 80.00`)이므로 **`isEqualByComparingTo`** 를 쓴다
  (값 동등성). Mockito 도 같은 이유로 **scale 민감한 `eq(BigDecimal)`(=`.equals` 기반)을 쓰지 않는다** —
  `anyDouble()`→`any()`, `eq(1000.0)`→ **`argThat(a -> a.compareTo(new BigDecimal("1000.0")) == 0)`**
  (값 동등성). `eq(new BigDecimal("1000.0"))` 는 요청 바디 리터럴이 `1000.00` 로 바뀌면 역직렬화 scale(2)과
  어긋나 값이 같아도 실패한다(`code-conventions.md` 의 "scale 민감 equals 금지" 규칙과도 충돌).

## 대안 (Alternatives)

- **정수(최소 화폐단위 `long`)**: 정밀·고성능이나 비율 할인·세율에서 변환이 번거롭고, 기존 소수
  단가(29.99) 표현·시드·테스트를 전부 재해석해야 해 파급이 더 크다. `BigDecimal` 이 최소 변경으로
  십진 정밀을 얻는다.
- **`double` 유지 + round 한 곳(현행 ADR-0003)**: 표현 오차가 남는다. 근본 해결이 목표라 기각.
- **금액 전용 `Money` 값 객체 도입**: 통화·정책 캡슐화에 이상적이나, 엔티티/DTO/직렬화에 커스텀 타입을
  끼우는 비용이 크다. 1차 전환은 표준 `BigDecimal` 로 하고, `Money` VO 는 후속 과제로 남긴다.

## 결과 (Consequences)

- (+) **십진 정밀**: 누계·비교·직렬화에서 부동소수 오차가 사라진다(B3 근본 해결).
- (+) 정밀도 정책(scale 2/HALF_UP)이 `MoneyUtils` + `@Column(scale=2)` 로 타입·스키마에 박힌다.
- (−) 산술이 장황(`a + b` → `a.add(b)`, `>` → `compareTo > 0`)하고 `null`/scale 함정이 생긴다
  (`equals` vs `compareTo`). 컨벤션으로 `compareTo`·`isEqualByComparingTo` 사용을 고정한다.
- (−) **응답 JSON 표현이 바뀐다**(위 4). 외부 소비자가 있다면 계약 변경 공지가 필요하다(현재는 내부 호출뿐).
- (−) 기존 파일 DB 는 수동 마이그레이션이 필요하다(위 3) — 멱등 `ALTER TABLE` 스크립트를 `db/migration/` 에 커밋했고 Flyway 가 없어 수동 1회 실행이다.
- **남은 과제**: 비율(`discountRate`)은 여전히 `double`(의도적). 통화 단위·`Money` VO·다중 통화는 범위 밖.

## 구현 범위 (인벤토리)

- **common-util**: `MoneyUtils`(round/applyTax/taxOf/multiply/discount/format, `TAX_RATE`).
- **ecommerce-service**: 엔티티 `Order`·`OrderItem`·`CartItem`·`Product`·`Coupon`(minOrderAmount);
  DTO `PricingResult`·`OrderResponse`·`OrderItemResponse`·`CartResponse`·`CartItemResponse`·
  `CreateProductRequest`·`ProductResponse`; 서비스 `PricingService`·`CartService`·`OrderService`;
  `client/dto/PaymentChargeRequest`·`PaymentRefundRequest`·`PaymentClient`; `DataSeeder`; 컨트롤러 변환부.
- **payment-service**: 엔티티 `Payment`·`Refund`·`Ledger`; DTO `ChargeRequest`·`RefundRequest`·
  `PaymentResponse`·`RefundResponse`; 서비스 `PaymentService`·`RefundService`; `PaymentController`.
- **admin**: `AdminPriceCalculator`·`RefundCommand`·`client/dto/PaymentRefundRequest`·
  `AdminOrderController.previewTotal`·`ShopGateway.refund`.
- **batch**: `OrderRow.totalAmount`·`SettlementJob`·`DailySalesAggregationJob`(누계·`DailySales.revenue`).
