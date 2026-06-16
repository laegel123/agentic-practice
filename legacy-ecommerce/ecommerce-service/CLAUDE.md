# ecommerce-service

> legacy-shop 의 **핵심 도메인 서비스**이자 **DB 스키마 소유자**(`:8081`). 상품·재고·장바구니·주문·
> 쿠폰·고객을 모두 소유하고, 결제만 payment 서비스에 HTTP 로 위임한다. 전체 시스템 맥락은
> 모노레포 문서 [`../docs/`](../docs/) 를, 이 모듈의 상세는 [`docs/architecture.md`](./docs/architecture.md) 를 본다.

## 정체성

- Java 21 / Spring Boot 3.5.15 / Gradle. 의존: `core-framework` + `common-util` +
  `spring-boot-starter-{web,data-jpa,validation}` + runtime `h2`.
- **모듈 중 유일하게 JPA 스키마를 생성**한다(`ddl-auto: update`). H2 파일 DB `~/legacyshopdb` 를
  batch 모듈과 공유한다(batch 는 `ddl-auto: none` 로 읽기만).
- 서비스 7 / 엔티티 8 / 컨트롤러 3 / 리포지토리 6 + native DAO 1. 주문 상태 `OrderStatus` enum 은
  batch 와 공유하려 `core-framework`(`com.legacy.shop.core.domain`)에 둔다.
  결제만 외부(`PaymentClient` → payment `:8082`)이고 나머지 로직·영속성은 전부 자체 보유.
- `@EnableJpaAuditing`, `@SpringBootApplication(scanBasePackages = "com.legacy.shop")` 로
  `core-framework` 의 공통 빈(`GlobalExceptionHandler` 등)을 함께 스캔한다.

## 빌드 / 실행 / 테스트

프로젝트 루트(`legacy-ecommerce/`)에서 실행한다. **Windows PowerShell**은 `.\gradlew.bat`,
Bash 도구(POSIX 셸)는 `./gradlew`.

```powershell
.\gradlew.bat :ecommerce-service:build      # 빌드
.\gradlew.bat :ecommerce-service:bootRun     # 실행 (:8081)
.\gradlew.bat :ecommerce-service:test        # 테스트 — 서비스 단위 + ProductSearchDaoTest + PaymentClientTest
```

> 테스트는 **현재 동작을 고정하는 characterization 테스트**다(JUnit5 + Mockito + AssertJ, 인메모리 H2
> `test` 프로파일로 실 DB 격리). 동작이 바뀌는 수정은 해당 단언을 **같은 커밋에서 의도적으로 뒤집어야**
> 한다 — "초록 만들기"로 약화시키지 말 것. 상세는 [`docs/architecture.md`](./docs/architecture.md) "테스트 / 검증 루프".

> ⚠️ **가장 먼저 기동**한다. 이 모듈이 스키마를 생성(`ddl-auto: update`)하므로
> payment·admin·batch 보다 먼저 떠 있어야 한다. 권장 순서: **ecommerce → payment → (admin / batch)**.
> H2 콘솔은 `/h2-console`(JDBC `jdbc:h2:file:~/legacyshopdb`, user `sa`, 비밀번호 없음).

## 구조 한눈에

```
src/main/java/com/legacy/shop/ecommerce/
├── EcommerceApplication.java     @EnableJpaAuditing · scanBasePackages="com.legacy.shop"
├── web/                          REST 컨트롤러 (얇게 → 서비스 위임 + DTO 변환)
│   ├── ProductController         /api/products
│   ├── CartController            /api/carts
│   └── OrderController           /api/orders
├── service/                      비즈니스 로직 (7개)
│   ├── OrderService              주문 오케스트레이션 7단계 (단계별 private 메서드 추출)
│   ├── CartService               장바구니 (cartTotal = 단가×수량 합산)
│   ├── InventoryService          재고 reserve(차감)/confirm(검증만)/restore(복원)
│   ├── PricingService            소계→할인→세금→합계 → PricingResult
│   ├── CouponService             쿠폰 검증 (만료일 당일 포함 유효)
│   ├── ProductService            상품 조회/검색/등록
│   └── CustomerService           고객 조회/가입
├── domain/                       JPA 엔티티 8 (DB 스키마 소유)
├── dto/                          요청/응답 record
├── repository/                   Spring Data JPA 6 + ProductSearchDao (native SQL — 파라미터 바인딩)
├── client/PaymentClient          payment 서비스 HTTP 호출 (타입 record client/dto/*)
└── config/                       DataSeeder(초기 시드) · RestTemplateConfig(connect 2s/read 5s)
```

| 메서드 | 경로 | 컨트롤러 |
|--------|------|----------|
| GET | `/api/products` | `ProductController.list` (페이징) |
| GET | `/api/products/{id}` | `ProductController.get` |
| GET | `/api/products/{id}/stock` | `ProductController.stock` |
| GET | `/api/products/search?keyword=` | `ProductController.search` |
| POST | `/api/products` | `ProductController.create` |
| POST | `/api/carts/{customerId}/items` | `CartController.addItem` |
| GET | `/api/carts/{customerId}` | `CartController.get` |
| POST | `/api/orders` | `OrderController.place` |
| GET | `/api/orders/{id}` | `OrderController.get` |
| GET | `/api/orders?customerId=` | `OrderController.byCustomer` |

## 이 모듈에서 일할 때 주의점

- **표준 풀스택 레이아웃의 레퍼런스 모듈**이다(`web → service → repository/client → domain`).
  컨트롤러는 얇게(인자 받기 → 서비스 호출 → `ApiResponse.success(...)` + DTO 변환), 비즈니스 로직은
  서비스에 둔다. 새 코드도 이 계층 규칙을 따른다.
- 공통 규칙 재확인: **생성자 주입만**(`private final`, `@Autowired`/Lombok 금지), DTO 는 `record`,
  엔티티는 `BaseTimeEntity` 상속·`@GeneratedValue(IDENTITY)`·`@Enumerated(STRING)`, 응답은
  `ApiResponse<T>`(성공 `code="0000"`), 오류는 `throw new BusinessException(ErrorCode.X)`.
- **금액 계산은 복제하지 말고 `PricingService`/`MoneyUtils` 를 재사용**한다 — 전사 `BigDecimal`
  (scale 2/HALF_UP), 비교는 `compareTo`([ADR-0006](../docs/adr/0006-money-as-bigdecimal.md)).
- **native 쿼리는 반드시 파라미터 바인딩**한다(`ProductSearchDao` 가 named parameter `:keyword` 사용 —
  문자열 결합은 SQL 인젝션). 서비스 간 HTTP 는 raw `Map` 이 아니라 타입 record(`client/dto/*`)로 주고받는다.
- ⚠️ 아직 남은 모듈 결함(FK 부재 R7 등)은 모노레포 [`../docs/known-issues.md`](../docs/known-issues.md) 를
  본다. 고칠 때는 같은 커밋에서 characterization 단언을 뒤집는다.

## 더 읽기

- 이 모듈: [`docs/architecture.md`](./docs/architecture.md) — 도메인 모델·주문 흐름 7단계·설정·시딩·테스트 루프 상세
- 모노레포 공통: [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) · [`../docs/known-issues.md`](../docs/known-issues.md) · [`../docs/adr/`](../docs/adr/)
