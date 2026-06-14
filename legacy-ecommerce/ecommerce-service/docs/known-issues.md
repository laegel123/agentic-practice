# ecommerce-service 알려진 문제 · 기술 부채

> 이 메모는 `ecommerce-service` 모듈에 한정된 결함/안티패턴을 모은다. 전체 코드베이스의 목록과
> 분류 체계는 모노레포 [`../../docs/known-issues.md`](../../docs/known-issues.md) 에 있다.
>
> 분류: 🐞 정합성/보안 결함 / ⚠️ 위험·안티패턴 / 🧹 정리. 영향도: 높음/중간/낮음.
> 수정 성격: **동작변경**(behavior-changing) / **동작보존**(behavior-preserving).
> B1·B2·B4 와 가격 계산은 이미 **현재 동작을 고정하는 characterization 테스트**가 있다
> (`src/test`, [`architecture.md`](./architecture.md) "테스트 / 검증 루프"). 동작변경 수정 시 해당
> 단언을 **같은 커밋에서 의도적으로 뒤집는다**. 아직 테스트가 없는 항목은 수정 전 먼저 박제한다.

## ecommerce 신규 발견 (모노레포 목록에 아직 없음)

| # | 위치 | 증상 | 영향 | 수정 성격 |
|---|------|------|------|----------|
| E1 | `repository/ProductSearchDao.java` — `searchByName()` | 🐞 **SQL 인젝션.** 검색어를 native SQL 에 그대로 이어붙인다(`... name LIKE '%" + keyword + "%'"`). `GET /api/products/search` 의 `@RequestParam keyword` 가 `ProductController → ProductService.search` 를 거쳐 무가공으로 도달 → 임의 SQL 주입 가능. | 높음 | 동작보존(파라미터 바인딩/`@Query` 로 전환) |

> E1 은 보안 결함이라 우선순위가 높다. 모노레포 `../../docs/known-issues.md` 에 정식 편입할
> 후보이며 현재는 이 모듈 문서에만 기록한다. 수정은 `em.createNativeQuery(...).setParameter(...)`
> 또는 Spring Data `@Query` + 바인딩 파라미터로, 키워드를 이스케이프해 처리한다.

## 모노레포 목록 중 ecommerce 해당 항목 (참조)

코드 체계는 [`../../docs/known-issues.md`](../../docs/known-issues.md) 와 동일하다. "테스트" 열은 현재
동작을 박제한 characterization 테스트 유무.

| # | 위치 | 내용 | 수정 성격 | 테스트 |
|---|------|------|----------|--------|
| B1 | `service/InventoryService.java` — `reserve()`/`confirm()` | 두 메서드가 동일하게 재고를 차감. `OrderService.placeOrder` 가 둘 다 호출 → 주문 1건당 **이중 차감**. | 동작변경 | ✅ `OrderServiceTest` |
| B2 | `service/CartService.java` — `cartTotal()` | `total += unitPrice` 로 수량 무시. 장바구니 합계 과소 계산. | 동작변경 | ✅ `CartServiceTest` |
| B4 | `service/CouponService.java` — `getValidCoupon()` | `!expiryDate.isAfter(today)` → 만료일 **당일 거부**(주석 "당일 포함"과 모순, off-by-one). | 동작변경 | ✅ `CouponServiceTest` |
| R1 | `service/OrderService.java` — `placeOrder()` | God method(재고/주문/쿠폰/가격/결제/장바구니/알림 7책임 + 외부 HTTP 를 한 `@Transactional` 에서). | 동작보존(추출) | ✅ `OrderServiceTest`(추출 안전망) |
| R2 | `client/PaymentClient.java` | 서비스 간 HTTP 를 타입 DTO 없이 raw `Map` 으로 주고받고 캐스팅. [ADR-0005](../../docs/adr/0005-map-based-inter-service-http.md) | 동작보존 | — |
| R5 | `application.yml`, `@Value` 기본값 | `payment.base-url`·DB 경로가 하드코딩. 운영 분리 불가. | 동작보존(외부화) | — |
| R7 | 엔티티 전반 (`Product.categoryId`, `Inventory.productId` 등) | FK 연관 없이 `Long` id 만 보유 → 참조 무결성 없음, 고아 데이터 가능. | 동작변경(스키마) | — |
| R8 | `config/RestTemplateConfig.java` | `new RestTemplate()` 타임아웃 미설정. 결제 지연 시 호출 스레드 무한 대기. | 동작보존 | — |
| C1 | `service/OrderService.java`(알림), `config/DataSeeder.java`(시드 로그) | `System.out.println` → SLF4J 로거로 교체. | 동작보존 | — |

## 연관 (모듈 외부지만 ecommerce 동작에 영향)

- **B3** — `MoneyUtils.round()` 가 `Math.floor`(버림). `PricingService` 의 모든 금액 계산이 이 함수를
  거친다. 소유는 **common-util**, 배경 [ADR-0003](../../docs/adr/0003-money-as-double.md). `PricingServiceTest` 가 현재(버림) 동작을 박제 중.
- **B5** — `PageRequestDto.getOffset()` 가 `page * size`(1-based 인데 첫 페이지를 건너뜀). 소유는
  **core-framework** 지만 증상은 `ProductService.list`(→ `GET /api/products`)에서 드러난다.
- **B6** — payment `RefundService.refund` 가 과다 환불을 막지 않는다. ecommerce 는 `PaymentClient.refund`
  로 이 경로를 호출하므로 함께 인지한다(소유는 **payment**).
- **CU1** — `CryptoUtils.hashPassword` 가 **MD5·무 salt**. `CustomerService.register` 가 사용한다(소유는 **common-util**).

## 우선 처리 제안

1. **E1**(보안, SQL 인젝션) — 파라미터 바인딩으로 전환. 테스트 선행 후.
2. **B1**(재고 정합성, 영향 높음) → **B2 / B4**(이미 박제됨 — 단언 뒤집기와 함께 수정).
3. **정리·동작보존**: C1(로깅), R8(타임아웃), R5(설정 외부화).
4. **구조 리팩토링**: R1(God method 추출 — `OrderServiceTest` 안전망 활용), R2(Map→DTO).
