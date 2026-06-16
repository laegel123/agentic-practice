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
| `domain` | `Product`, `Inventory`, `Category`, `Customer`, `Cart`, `CartItem`, `Order`, `OrderItem` (+ `OrderStatus` enum) | JPA 엔티티 = DB 스키마 |
| `dto` | 요청/응답 `record` 8종 | API 입출력 |
| `repository` | `Product/Inventory/Category/Customer/Cart/Order/CouponRepository` + `ProductSearchDao` | 영속성. 대부분 Spring Data 파생 쿼리, 검색만 native DAO |
| `client` | `PaymentClient` | payment 서비스 HTTP 호출 (RestTemplate, raw `Map`) |
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
| `Product` | `product` | `name`, `price`(double), `categoryId`(Long, FK 없음), `description`, `active` | — |
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

`OrderStatus` enum 은 `CREATED → PAID → CANCELLED` 이며 `@Enumerated(STRING)` 로 저장한다.

## 엔드포인트

모든 응답은 `ApiResponse<T> = { code, message, data }`(성공 `code="0000"`). 요청/응답 본문은
`dto/` 의 `record`.

| 메서드 | 경로 | 컨트롤러 | 요청 → 응답 |
|--------|------|----------|-------------|
| GET | `/api/products` | `ProductController.list` | `PageRequestDto` → `List<ProductResponse>` (⚠ offset B5) |
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

`POST /api/orders` 한 번이 아래 7단계를 **하나의 `@Transactional`** 에서 처리한다(⚠ God method R1).

```
OrderController.place(PlaceOrderRequest)
  → OrderService.placeOrder(customerId, couponCode)
     1) 장바구니 로드            cartRepository.findByCustomerId (없거나 비면 EMPTY_CART)
     2) 재고 확인 + 예약(차감)    각 항목 checkStock → InventoryService.reserve   ◀ 1차 차감
     3) Order/OrderItem 생성     상품 조회 → 스냅샷(productName, unitPrice, lineTotal)
     4) 쿠폰 + 금액 계산         couponService.getValidCoupon → PricingService.calculate
                                  → PricingResult(소계·할인·세금·합계) 를 Order 에 반영
     5) 결제 호출(HTTP)          PaymentClient.charge(orderId, customerId, total) → payment :8082
                                  실패 시 PAYMENT_FAILED. 성공 시 paymentId 저장 + status=PAID
     6) 재고 확정                각 항목 InventoryService.confirm                ◀ 검증만(차감 없음, B1 ✅)
     7) 장바구니 비우기 + 알림    cart.clear() / System.out.println (⚠ C1)
  → ApiResponse.success(OrderResponse)
```

✅ **재고 이중차감(B1) 수정됨(2026-06-16)**: (이전) 2단계 `reserve()` 와 6단계 `confirm()` 가 동일하게
`quantity - qty` 를 수행해(별도 "예약" 상태가 없음) 주문 1건당 재고가 2배로 빠졌다. → **`confirm()` 은
더 이상 차감하지 않고 재고 레코드 존재만 검증**한다. 차감은 2단계 `reserve()` 에서 1회만 일어난다.
회귀 테스트는 `InventoryServiceTest`(reserve→confirm 단일차감).
⚠️ **God method(R1)**: 재고·주문·쿠폰·가격·결제·장바구니·알림이 한 메서드/한 트랜잭션에 묶여 있어
결제(외부 HTTP)가 트랜잭션 안에서 호출되고, 재고 복원(`restore`)·보상 로직도 빠져 있다.

가격 계산은 `PricingService.calculate(items, coupon)` 가 담당한다: `소계 = Σ(unitPrice×quantity)`,
`할인 = round(소계 × discountRate)`(쿠폰 있고 `소계 ≥ minOrderAmount` 일 때), `세금 = round(소계 × TAX_RATE)`,
`합계 = round(소계 + 세금 − 할인)`. 모든 금액이 `MoneyUtils.round`(현재 **버림** = B3)를 거친다.

## 서비스 간 통신

`client/PaymentClient` 가 payment 서비스(`:8082`)를 RestTemplate 으로 호출한다.

```
PaymentClient.charge(orderId, customerId, amount)
  → req = HashMap{orderId, customerId, amount}                  (타입 DTO 아님 — raw Map, R2)
  → RestTemplate.postForObject(paymentBaseUrl + "/api/payments/charge", req, Map.class)
  → resp.get("data").get("paymentId") 캐스팅 → Long             (계약이 코드에 안 드러남)
PaymentClient.refund(paymentId, amount)  → POST /api/payments/refund
```

⚠️ raw `Map` 통신(R2, [ADR-0005](../../docs/adr/0005-map-based-inter-service-http.md)),
`@Value` 로 주입한 하드코딩 URL(R5), `RestTemplateConfig` 의 타임아웃 미설정(R8)이 함께 걸려 있다.

## 설정 (`src/main/resources/application.yml`)

```yaml
server:
  port: 8081
spring:
  datasource:
    url: jdbc:h2:file:~/legacyshopdb;AUTO_SERVER=TRUE   # batch 와 공유하는 파일 DB
    username: sa
    password:                                            # 비밀번호 없음
  jpa:
    hibernate:
      ddl-auto: update                                   # ⚠ 이 모듈이 스키마를 생성·갱신
  h2:
    console: { enabled: true, path: /h2-console }
payment:
  base-url: http://localhost:8082                        # ⚠ 하드코딩 기본값 (R5)
```

`payment.base-url` 은 `PaymentClient` 생성자에서 `@Value("${payment.base-url:http://localhost:8082}")`
로 주입한다. 공유 H2 파일 DB 배경은 [ADR-0002](../../docs/adr/0002-shared-h2-file-database.md).

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
| `service/CartServiceTest` | `CartService.cartTotal` | 수량 무시(10×2 + 20×3 이지만 결과 30.0 = unitPrice 합, ⚠ B2) |
| `service/CouponServiceTest` | `CouponService.getValidCoupon` | 만료일 **당일 거부**(⚠ B4), 익일 유효/전일 만료/미존재/빈 코드 |
| `service/PricingServiceTest` | `PricingService.calculate` | 소계=Σ(단가×수량), 세금 10%, 쿠폰 최소주문 조건, `round`(버림) |
| `EcommerceApplicationTests` | 스프링 컨텍스트 | 전체 빈 배선 스모크(`@ActiveProfiles("test")`) |

테스트는 **인메모리 H2 프로파일**(`src/test/resources/application-test.yml` — `jdbc:h2:mem:testdb`,
`ddl-auto: create-drop`)로 돌아 운영 파일 DB(`~/legacyshopdb`)를 건드리지 않는다. 단위 테스트는
순수 Mockito 라 컨텍스트를 띄우지 않는다. 위 표 중 `InventoryServiceTest`(5개)는 B1 수정의 회귀이고
나머지는 characterization, 그리고 이 모듈은 추가로 `repository/ProductSearchDaoTest`(E1 SQL 인젝션 회귀,
`@DataJpaTest` 3개)를 둔다. 모노레포 전체는 **45개** = characterization 28 + 버그수정 회귀 5(B1
`InventoryServiceTest`; B6 는 `RefundServiceTest` 단언을 차단으로 뒤집어 흡수) + 보안 회귀 12(E1
`ProductSearchDaoTest` 3 + admin A1 `AdminRefundControllerTest` 3 + common-util CU1 `CryptoUtilsTest` 6).

## 의존성 / 기동 순서

- **빌드 의존**(`build.gradle`): `core-framework`, `common-util`, `spring-boot-starter-{web,data-jpa,validation}`,
  runtime `h2`. 테스트 번들 `spring-boot-starter-test`(JUnit5 + Mockito + AssertJ) + `junit-platform-launcher`.
- **런타임 의존**: payment `:8082`(주문 시 결제 호출). admin·batch 는 이 모듈/DB 에 의존하므로
  **ecommerce → payment → (admin / batch)** 순으로 기동한다
  ([`../../docs/architecture.md`](../../docs/architecture.md) "실행 방법").
