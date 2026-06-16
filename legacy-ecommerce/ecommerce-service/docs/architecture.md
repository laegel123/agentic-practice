# ecommerce-service 아키텍처

> 이 문서는 `ecommerce-service` 모듈 **내부** 구조에 집중한다. 모듈 간 통신·DB 토폴로지·전체 그림은
> 모노레포 [`../../docs/architecture.md`](../../docs/architecture.md) 를 본다.

## 책임

상품·재고·장바구니·주문·쿠폰·고객 도메인을 소유하는 **핵심 커머스 서비스**다. 포트는 `:8081`.
결제만 payment 서비스에 HTTP 로 위임하고, 그 외 비즈니스 로직과 영속성은 전부 자체 보유한다.

이 모듈은 시스템의 **DB 스키마 소유자**다. JPA `ddl-auto: update` 로 H2 파일 DB `~/legacyshopdb`
의 테이블을 생성·갱신하며, batch 모듈은 같은 DB 를 `ddl-auto: none` 로 **읽기만** 한다(스키마를
만들지 않는다). 따라서 ecommerce 가 **가장 먼저 기동**해야 한다.

## 패키지 / 계층

공통 컨벤션의 표준 풀스택 레이아웃을 그대로 따른다. 흐름은 단방향이다.

```
web (컨트롤러, 얇게)
  → service (비즈니스 로직 · @Transactional 경계)
      → repository (Spring Data JPA) / client (PaymentClient → payment :8082)
          → domain (JPA 엔티티 = DB 스키마)
```

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `web` | `ProductController`, `CartController`, `OrderController` | REST 엔드포인트. 얇게 유지 → 서비스 위임 + DTO 변환 |
| `service` | `OrderService`, `CartService`, `InventoryService`, `PricingService`, `CouponService`, `ProductService`, `CustomerService` (+ `PricingResult` record) | 비즈니스 로직, 트랜잭션 경계 |
| `domain` | `Product`, `Inventory`, `Category`, `Customer`, `Cart`, `CartItem`, `Order`, `OrderItem` (주문 상태 `OrderStatus` enum 은 BT2 ✅ `core-framework` 로 이동) | JPA 엔티티 = DB 스키마 |
| `dto` | 요청/응답 `record` 8종 | API 입출력 |
| `repository` | `Product/Inventory/Category/Customer/Cart/Order/CouponRepository` + `ProductSearchDao` | 영속성. 대부분 Spring Data 파생 쿼리, 검색만 native DAO |
| `client` | `PaymentClient` (+ `client/dto/*` record) | payment 서비스 HTTP 호출 (RestTemplate; R2 ✅ 타입 record) |
| `config` | `DataSeeder`, `RestTemplateConfig` | 초기 시드 적재, `RestTemplate` 빈 |
| (루트) | `EcommerceApplication` | `@EnableJpaAuditing`, `scanBasePackages = "com.legacy.shop"` |

> `scanBasePackages = "com.legacy.shop"` 로 그룹 전체를 스캔해야 `core-framework` 의
> `GlobalExceptionHandler`(`@RestControllerAdvice`)·`ApiResponse`·`BaseTimeEntity` 등이 함께 잡힌다.
> `@EnableJpaAuditing` 로 `BaseTimeEntity` 의 `createdAt`/`updatedAt` 이 자동 기록된다.

## 도메인 모델

모든 엔티티는 `BaseTimeEntity`(core-framework) 를 상속하고 `@GeneratedValue(IDENTITY)` 를 쓴다.
연관은 **`Long` id 참조만** 하고 JPA FK 관계(`@ManyToOne` 등)를 맺지 않는다(⚠ R7 — 참조 무결성 없음).
예외는 집합 루트→자식(`Cart→CartItem`, `Order→OrderItem`)뿐이다.

| 엔티티 | 테이블 | 핵심 필드 | 관계 |
|--------|--------|----------|------|
| `Product` | `product` | `name`, `price`(`BigDecimal`, scale 2 — ADR-0006), `categoryId`(Long, FK 없음), `description`, `active` | — |
| `Inventory` | `inventory` | `productId`(Long), `quantity`(int) | Product 와 id 로만 연결 |
| `Category` | `category` | `name` | — |
| `Customer` | `customer` | `email`, `name`, `phone`, `password`(MD5·무 salt, ⚠ CU1) | — |
| `Cart` | `cart` | `customerId` | `@OneToMany(cascade=ALL, orphanRemoval=true)` → `CartItem` (`@JoinColumn cart_id`) |
| `CartItem` | `cart_item` | `productId`, `quantity`, `unitPrice`(담을 때 시점 가격 스냅샷) | Cart 자식 |
| `Order` | `orders` | `customerId`, `subtotal`, `discountAmount`, `tax`, `totalAmount`, `status`, `couponCode`, `orderedAt`(UTC), `paymentId` | `@OneToMany(cascade=ALL, orphanRemoval=true, fetch=LAZY)` → `OrderItem` (`@JoinColumn order_id`) |
| `OrderItem` | `order_item` | `productId`, `productName`, `unitPrice`, `quantity`, `lineTotal` (주문 시점 스냅샷) | Order 자식 |
| `Coupon` | `coupon` | `code`, `discountRate`(0.1=10%), `expiryDate`(LocalDate, 당일 포함), `minOrderAmount` | — |

```
Cart ──1:N──▶ CartItem        (cascade ALL, orphanRemoval — 주문 후 clear)
Order ─1:N──▶ OrderItem        (cascade ALL, orphanRemoval, LAZY)
Product ··id··▶ Inventory       (FK 없음, productId 로만 연결)
Product ··id··▶ Category        (FK 없음, categoryId 로만 연결)
Coupon / Customer               (독립)
```

`OrderStatus` enum 은 `CREATED → PAID → CANCELLED` 이며 `@Enumerated(STRING)` 로 저장한다. 이 enum 은
BT2 ✅ 로 batch 와 공유하기 위해 `core-framework`(`com.legacy.shop.core.domain.OrderStatus`)에 단일 정의되어
있다(이전엔 ecommerce·batch 가 각자 복제 → 드리프트 위험).

## 엔드포인트

모든 응답은 `ApiResponse<T> = { code, message, data }`(성공 `code="0000"`). 요청/응답 본문은
`dto/` 의 `record`.

| 메서드 | 경로 | 컨트롤러 | 요청 → 응답 |
|--------|------|----------|-------------|
| GET | `/api/products` | `ProductController.list` | `PageRequestDto` → `List<ProductResponse>` (offset B5 ✅ `(page-1)*size`) |
| GET | `/api/products/{id}` | `ProductController.get` | → `ProductResponse` |
| GET | `/api/products/{id}/stock` | `ProductController.stock` | → `Integer` |
| GET | `/api/products/search?keyword=` | `ProductController.search` | `keyword` → `List<ProductResponse>` (⚠ SQL 인젝션 E1) |
| POST | `/api/products` | `ProductController.create` | `CreateProductRequest` → `ProductResponse` (상품+초기재고 동시 생성) |
| POST | `/api/carts/{customerId}/items` | `CartController.addItem` | `AddCartItemRequest` → `CartResponse` |
| GET | `/api/carts/{customerId}` | `CartController.get` | → `CartResponse` (없으면 생성) |
| POST | `/api/orders` | `OrderController.place` | `PlaceOrderRequest(customerId, couponCode?)` → `OrderResponse` |
| GET | `/api/orders/{id}` | `OrderController.get` | → `OrderResponse` |
| GET | `/api/orders?customerId=` | `OrderController.byCustomer` | → `List<OrderResponse>` |

> `CartResponse.total` 은 `CartService.cartTotal()` 로 채우는데 수량을 무시한다(⚠ B2). 단,
> 개별 `CartItemResponse.lineTotal` 은 `unitPrice * quantity` 로 올바르게 계산된다(컨트롤러에서 직접).

## 핵심 흐름 — 주문 처리 (`OrderService.placeOrder`)

`POST /api/orders` 한 번이 아래 7단계를 **하나의 `@Transactional`** 에서 처리한다. R1 ✅ 로 각 단계를
의도가 드러나는 private 메서드로 추출했고, `placeOrder` 는 이 흐름만 보여준다.

```
OrderController.place(PlaceOrderRequest)
  → OrderService.placeOrder(customerId, couponCode)   ── 아래 7단계를 private 메서드로 위임(R1 ✅)
     1) 장바구니 로드            loadNonEmptyCart()     (없거나 비면 EMPTY_CART)
     2) 재고 확인 + 예약(차감)    reserveStock()         각 항목 checkStock → reserve   ◀ 1차 차감
     3) Order/OrderItem 생성     buildOrder()           상품 조회 → 스냅샷(productName, unitPrice, lineTotal)
     4) 쿠폰 + 금액 계산         applyPricing()         getValidCoupon → PricingService.calculate → PricingResult 반영
     5) 결제 호출(HTTP)          pay()                  PaymentClient.charge(...) → payment :8082; 성공 시 paymentId + status=PAID
     6) 재고 확정                confirmStock()         각 항목 confirm                ◀ 검증만(차감 없음, B1 ✅)
     7) 장바구니 비우기 + 알림    clearCart() / notifyOrderPlaced()   cart.clear() / log.info (SLF4J — C1 ✅)
  → ApiResponse.success(OrderResponse)
```

✅ **재고 이중차감(B1) 수정됨(2026-06-16)**: (이전) 2단계 `reserve()` 와 6단계 `confirm()` 가 동일하게
`quantity - qty` 를 수행해(별도 "예약" 상태가 없음) 주문 1건당 재고가 2배로 빠졌다. → **`confirm()` 은
더 이상 차감하지 않고 재고 레코드 존재만 검증**한다. 차감은 2단계 `reserve()` 에서 1회만 일어난다.
회귀 테스트는 `InventoryServiceTest`(reserve→confirm 단일차감).
✅ **God method 추출됨(R1, 2026-06-16)**: 7책임을 위 private 메서드들로 분리해 `placeOrder` 는 단계 흐름만
남겼다(동작보존 — `OrderServiceTest` 단언 무변). 다만 구조 개선과 **별개로**, 결제(외부 HTTP)가 트랜잭션
안에서 호출되고 재고 복원(`restore`)·보상 로직이 없는 점은 그대로다 — 트랜잭션 경계/사가(saga) 설계 과제로 남는다.

가격 계산은 `PricingService.calculate(items, coupon)` 가 담당한다: `소계 = Σ(unitPrice×quantity)`,
`할인 = round(소계 × discountRate)`(쿠폰 있고 `소계 ≥ minOrderAmount` 일 때), `세금 = round(소계 × TAX_RATE)`,
`합계 = round(소계 + 세금 − 할인)`. 모든 금액이 `MoneyUtils.round`(B3 ✅ 수정으로 **HALF_UP 반올림**)를 거친다.
금액 타입은 ✅ **`BigDecimal`(scale 2)** 이다(2026-06-16 `double`→`BigDecimal` 전환 — [ADR-0006](../../docs/adr/0006-money-as-bigdecimal.md)); 위 산식의 값은 종전과 동일하고 표현만 정밀해졌다.

## 서비스 간 통신

`client/PaymentClient` 가 payment 서비스(`:8082`)를 RestTemplate 으로 호출한다.

```
PaymentClient.charge(orderId, customerId, amount)
  → req = PaymentChargeRequest(orderId, customerId, amount)     (타입 record — R2 ✅)
  → RestTemplate.exchange(paymentBaseUrl + "/api/payments/charge", POST,
                          HttpEntity(req), ParameterizedTypeReference<ApiResponse<PaymentChargeResponse>>)
  → resp.getData().paymentId()  → Long                          (캐스팅 제거; 계약이 record 로 드러남)
PaymentClient.refund(paymentId, amount)  → PaymentRefundRequest 로 POST /api/payments/refund
```

✅ **raw `Map` 통신(R2) 수정됨(2026-06-16)**: 요청·응답을 타입 record(`client/dto/*`)로 교체하고
`((Number) data.get("paymentId")).longValue()` 캐스팅을 제거했다([ADR-0005](../../docs/adr/0005-map-based-inter-service-http.md)).
회귀 `PaymentClientTest`(MockRestServiceServer 와이어 계약). 호출 대상 URL·DB 접속정보는 환경변수로
외부화됐다(R5 ✅, [ADR-0007](../../docs/adr/0007-config-via-environment-variables.md)).
(`RestTemplateConfig` 타임아웃 미설정 R8 도 ✅ 수정됨 — connect 2s/read 5s.)

## 설정 (`src/main/resources/application.yml`)

```yaml
server:
  port: 8081
spring:
  datasource:
    url: ${SHOP_DB_URL:jdbc:h2:file:~/legacyshopdb;AUTO_SERVER=TRUE}  # batch 와 공유(같은 SHOP_DB_*)
    username: ${SHOP_DB_USERNAME:sa}
    password: ${SHOP_DB_PASSWORD:}                       # 기본 빈 값(H2 로컬)
  jpa:
    hibernate:
      ddl-auto: update                                   # ⚠ 이 모듈이 스키마를 생성·갱신
  h2:
    console: { enabled: true, path: /h2-console }
payment:
  base-url: ${PAYMENT_BASE_URL:http://localhost:8082}    # 환경변수 외부화, 미설정 시 로컬 기본값
```

`payment.base-url` 은 `PaymentClient` 생성자에서 `@Value("${payment.base-url:http://localhost:8082}")`
로 주입한다. URL·DB 접속정보의 환경변수 외부화는 [ADR-0007](../../docs/adr/0007-config-via-environment-variables.md),
공유 H2 파일 DB 배경은 [ADR-0002](../../docs/adr/0002-shared-h2-file-database.md).

## 데이터 시딩 (`config/DataSeeder`)

`CommandLineRunner` 로 기동 시 `product` 테이블이 비어 있을 때만 1회 적재한다.

- 카테고리 2(전자기기/사무용품), 상품 12 + 각 재고(페이징·검색 확인용, 가격은 소수점 둘째자리).
- 고객 1명(`hong@example.com` / 비밀번호 `CryptoUtils.hashPassword` = PBKDF2+임의 salt. 레거시 행은 MD5 일 수 있고 CU1 검증 폴백으로 그대로 통과).
- 쿠폰 2종: `SAVE20`(20%, 만료 +1년), `WELCOME`(10%, **만료일=오늘**). `WELCOME` 은 B4 off-by-one
  때문에 시드 당일 곧바로 거부되는 경계 케이스다.
- 이력 주문 3건(PAID 100/50, CANCELLED 30) — batch 정산/집계 확인용. `orderedAt = DateUtils.now()`(UTC).

## 테스트 / 검증 루프

`src/test` 에 **현재 동작(버그 포함)을 고정하는 characterization 테스트**가 있다. 목적은 정합성이
아니라 *지금 코드가 실제로 하는 일*을 박제해, 향후 버그 수정·리팩토링의 안전망을 만드는 것이다.
버그를 고치면 동작이 바뀌므로 해당 단언을 **같은 커밋에서 의도적으로 뒤집는다**(green 으로 약화 금지).

| 테스트 | 대상 | 고정하는 동작 |
|--------|------|--------------------|
| `service/OrderServiceTest` | `OrderService.placeOrder` (협력자 7개 Mockito mock) | `reserve`·`confirm` 모두 호출(R1 안전망; confirm 은 B1 수정 후 비차감), 결제 실패 시 `confirm` 미호출·장바구니 유지, 빈 장바구니 → `EMPTY_CART` |
| `service/InventoryServiceTest` | `InventoryService.reserve/confirm/restore` | **B1 ✅ 회귀**: reserve 차감·confirm 불변·reserve→confirm 단일차감(50→48, 이전 46)·미존재 예외·restore 복원 |
| `service/CartServiceTest` | `CartService.cartTotal` | **B2 ✅ 회귀**: 단가×수량 합산(10×2 + 20×3 = 80.0, 이전 30.0), 빈 장바구니 0 |
| `service/CouponServiceTest` | `CouponService.getValidCoupon` | **B4 ✅ 회귀**: 만료일 **당일 유효**(이전 거부), 익일 유효/전일 만료/미존재/빈 코드 |
| `service/PricingServiceTest` | `PricingService.calculate` | 소계=Σ(단가×수량), 세금 10%, 쿠폰 최소주문 조건, `round`(B3 ✅ 이제 HALF_UP — 이 시나리오들은 정확히 떨어져 값 불변) |
| `service/ProductServiceTest` | `ProductService.list` (Mockito) | **B5 ✅ 회귀**: 1-based 첫 페이지가 P1~P5 반환(이전엔 P6~P10 로 건너뜀), 둘째 페이지 P6~P10, 범위 밖 빈 결과 |
| `client/PaymentClientTest` | `PaymentClient.charge/refund` (MockRestServiceServer) | **R2 ✅ 회귀**: charge 요청 바디 `{orderId,customerId,amount}`(method 미포함)·`ApiResponse.data.paymentId` 추출(나머지 필드 무시), refund 요청 바디 `{paymentId,amount}` |
| `EcommerceApplicationTests` | 스프링 컨텍스트 | 전체 빈 배선 스모크(`@ActiveProfiles("test")`) |

테스트는 **인메모리 H2 프로파일**(`src/test/resources/application-test.yml` — `jdbc:h2:mem:testdb`,
`ddl-auto: create-drop`)로 돌아 운영 파일 DB(`~/legacyshopdb`)를 건드리지 않는다. 단위 테스트는
순수 Mockito 라 컨텍스트를 띄우지 않는다. 위 표 중 `InventoryServiceTest`(5개)는 B1, `CartServiceTest`·
`CouponServiceTest` 는 B2·B4 수정의 회귀이고(단언을 같은 커밋에서 뒤집음), `ProductServiceTest`(3개)는 B5 페이징
회귀이며, 그리고 이 모듈은 추가로 `repository/ProductSearchDaoTest`(E1 SQL 인젝션 회귀, `@DataJpaTest` 3개)와
`client/PaymentClientTest`(R2 와이어 계약 회귀, MockRestServiceServer 2개)를 둔다.
모노레포 전체는 **79개** = characterization 28 + 버그수정 회귀 17(B1 `InventoryServiceTest` 5 + BT1·B7 batch
`SettlementJobTest`·`DailySalesAggregationJobTest` 5 + B5 core-framework `PageRequestDtoTest` 4·ecommerce `ProductServiceTest` 3;
B2·B3·B4·B6 는 기존 characterization 단언을 뒤집어 흡수) + 보안 회귀 12(E1 `ProductSearchDaoTest` 3 +
admin A1 `AdminRefundControllerTest` 3 + common-util CU1 `CryptoUtilsTest` 6) + 동작보존 정리 회귀 8(R4 core-framework
`GlobalExceptionHandlerTest` 2 + R6 admin `AdminPriceCalculatorTest` 3 + CU2 common-util `JsonUtilsTest` 3) +
구조 리팩토링 회귀 2(R2 ecommerce `PaymentClientTest` 2; R1·BT2 는 기존 테스트가 안전망) +
리뷰 후속 보강 10(B5 page≤0 클램프 `PageRequestDtoTest`+2·`ProductServiceTest`+2 + A1 후속 admin `AdminAuthTest` 3 +
B6 후속 음수환불 `RefundServiceTest`+2 + CU1 후속 `CryptoUtilsTest` hashPassword null +1) +
BigDecimal 전환 회귀(ADR-0006 — 금액 단언을 `isEqualByComparingTo` 로 전환[값 보존] + `MoneyUtilsTest` scale 2 검증 1개 추가).

## 의존성 / 기동 순서

- **빌드 의존**(`build.gradle`): `core-framework`, `common-util`, `spring-boot-starter-{web,data-jpa,validation}`,
  runtime `h2`. 테스트 번들 `spring-boot-starter-test`(JUnit5 + Mockito + AssertJ) + `junit-platform-launcher`.
- **런타임 의존**: payment `:8082`(주문 시 결제 호출). admin·batch 는 이 모듈/DB 에 의존하므로
  **ecommerce → payment → (admin / batch)** 순으로 기동한다
  ([`../../docs/architecture.md`](../../docs/architecture.md) "실행 방법").
