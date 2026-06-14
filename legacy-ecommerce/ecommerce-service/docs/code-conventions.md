# ecommerce-service 코드 컨벤션

> 이 모듈은 모노레포 공통 컨벤션 [`../../docs/code-conventions.md`](../../docs/code-conventions.md) 을
> 그대로 따른다(패키지 구조, 생성자 주입, DTO=`record`, `ApiResponse`/`BusinessException` 에러 처리,
> 엔티티 규칙, 한국어 주석/영어 식별자, 빌드 규칙 등). 아래에는 **ecommerce 에 한정된 보충·차이점만** 적는다.

## 표준 풀스택 레이아웃의 레퍼런스

이 모듈은 `web / service / domain / dto / repository / client / config` 전 계층을 갖춘 **표준 레이아웃의
기준 모듈**이다(데이터를 소유하지 않는 `admin` 과 대비). 새 기능도 이 계층 경계를 지킨다.

- **컨트롤러는 얇게**: 인자 받기 → 서비스 호출 → `ApiResponse.success(...)` 래핑 + 엔티티→DTO 변환.
  DTO 변환(`toDto`)은 컨트롤러의 `private` 메서드로 둔다(`CartController`, `OrderController` 참고).
- **트랜잭션 경계는 서비스**: `@Transactional` 은 서비스 메서드(`placeOrder`, `addItem`, `getOrCreateCart`)에.
- **비즈니스 로직은 서비스에만**. 단, 현재 `OrderService.placeOrder` 는 7책임을 한 메서드에 모은
  God method(⚠ R1)이니 **새 로직을 여기에 더 얹지 말고** 협력 서비스로 분리한다.

## 엔티티 / 영속성 규칙

- 공통 규칙대로 `BaseTimeEntity` 상속 · `@GeneratedValue(IDENTITY)` · enum 은 `@Enumerated(STRING)`.
  `setId` 를 두지 않는다(`Product` 등 — 테스트는 `ReflectionTestUtils` 로 id 주입).
- 집합 루트→자식은 `@OneToMany(cascade = ALL, orphanRemoval = true)` + `@JoinColumn` 단방향으로 맺는다
  (`Cart→CartItem`, `Order→OrderItem`). 자식에 역참조(`@ManyToOne`)를 두지 않는 현 스타일을 유지한다.
- **스냅샷 패턴**: `CartItem.unitPrice`(담는 시점 가격), `OrderItem`(주문 시점 `productName`/`unitPrice`/`lineTotal`)
  은 원본 변경과 무관하게 값을 고정한다. 주문/장바구니 항목은 이 스냅샷 규칙을 따른다.
- ⚠️ 엔티티 간 연관은 **FK 없이 `Long` id 참조만** 한다(`Product.categoryId`, `Inventory.productId` 등 R7).
  참조 무결성이 보장되지 않으니 인지하되, 현 패턴을 임의로 바꾸지 않는다(스키마 영향).

## 가격·금액 계산

- 금액은 전사적으로 `double`([ADR-0003](../../docs/adr/0003-money-as-double.md)). 모든 계산은
  `MoneyUtils.round`(현재 **버림** = B3)와 상수 `MoneyUtils.TAX_RATE` 를 거친다.
- **계산 로직은 `PricingService` 한 곳**에 모은다: `소계 → (조건부)할인 → 세금 → 합계`, 결과는
  `PricingResult` record. 새 금액 계산이 필요해도 복제하지 말고 `PricingService`/`MoneyUtils` 를 재사용한다
  (admin `AdminPriceCalculator` 가 이 계산식을 복붙한 R6 가 반면교사).

## 리포지토리 / 쿼리

- 기본은 **Spring Data 파생 쿼리**(`findByCustomerId`, `findByActiveTrue`, `findByCode` 등).
- ⚠️ 검색만 `ProductSearchDao` 가 `EntityManager` + native SQL 을 쓰는데, **검색어를 쿼리 문자열에
  직접 이어붙여 SQL 인젝션에 노출**된다(E1). 새 쿼리는 이 방식을 따르지 말고 파생 쿼리나
  **파라미터 바인딩**(`setParameter`)·`@Query` 를 쓴다 — [`known-issues.md`](./known-issues.md).

## 서비스 간 호출

- payment 호출은 `client/PaymentClient` 한 곳으로 캡슐화한다. ⚠️ 현재 raw `Map` 통신(R2,
  [ADR-0005](../../docs/adr/0005-map-based-inter-service-http.md))·`RestTemplate` 타임아웃 미설정(R8)이다.
  새 통신을 추가하면 타입 DTO 와 타임아웃을 적용하고, 응답 캐스팅을 클라이언트 안으로 가둔다.

## 테스트 작성 패턴

`src/test` 의 기존 테스트와 같은 스타일로 작성한다(상세 목록은 [`architecture.md`](./architecture.md)
"테스트 / 검증 루프").

- **단위 테스트는 순수 Mockito**(`@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`),
  스프링 컨텍스트를 띄우지 않는다. 협력자가 없는 계산기(`PricingService`)는 그냥 `new` 한다.
- **AssertJ** 단언(`assertThat`), `double` 비교는 `within(EPS)`, 예외는 `catchThrowableOfType` 으로
  `BusinessException.getErrorCode()` 를 확인한다.
- **characterization 우선**: 버그 동작도 *현재 그대로* 박제하고, 클래스 주석에 "무엇을·왜 박제하는지"와
  대응 이슈 코드(예: `docs/known-issues.md B2`)를 적는다. 버그를 고칠 땐 단언을 **같은 커밋에서 뒤집는다**.
- 컨텍스트가 필요한 테스트는 `@SpringBootTest @ActiveProfiles("test")` 로 인메모리 H2(`application-test.yml`)
  를 쓴다 — 운영 파일 DB(`~/legacyshopdb`)를 절대 건드리지 않는다.

## ⚠️ 모방 금지 안티패턴 (이 모듈에 존재)

손대는 김에 개선하되, **새 코드에서 그대로 답습하지 않는다.** 코드(B/R/C)는 모노레포
[`../../docs/known-issues.md`](../../docs/known-issues.md) 체계, `E#` 는 이 모듈 신규 발견.

- **E1** — `ProductSearchDao` native SQL 문자열 연결(SQL 인젝션).
- **B1** — `InventoryService.reserve()`/`confirm()` 동일 차감(재고 이중차감). **B2** — `cartTotal` 수량 무시.
  **B4** — 쿠폰 만료 당일 거부. **B5** — `PageRequestDto.getOffset` 페이징 오프셋(core-framework, 여기서 사용).
- **R1** — `OrderService.placeOrder` God method. **R2** — `PaymentClient` raw `Map`. **R5** — 하드코딩 설정.
  **R7** — 엔티티 FK 미사용. **R8** — `RestTemplate` 타임아웃 없음.
- **C1** — `OrderService`/`DataSeeder` 의 `System.out.println` → SLF4J 로거로 교체.

## 주석 / 언어

- 본문 주석·도메인 용어는 한국어, 식별자/타입은 영어(공통 규칙과 동일).
- 클래스 상단에 한 줄 `/** ... */` 로 역할을 적는 기존 스타일을 유지한다(`OrderService`, `PricingService` 참고).
  과도한 주석은 더하지 않는다.
