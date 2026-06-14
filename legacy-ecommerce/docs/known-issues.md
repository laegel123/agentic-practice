# 알려진 문제 · 기술 부채

> 코드 탐색 중 확인된 결함/안티패턴 목록이다. **이번 패스에서는 코드 동작을 바꾸지 않는다**
> (문서화 우선). 각 항목은 향후 리팩토링 대상이며, 수정 전에 반드시 **현재 동작을 고정하는
> characterization 테스트**를 먼저 작성해야 한다(대부분 동작이 바뀌는 버그 수정이기 때문).
>
> 분류: 🐞 정합성 버그 / ⚠️ 위험·안티패턴 / 🧹 정리.
> 영향도: 높음/중간/낮음. 수정 성격: **동작변경**(behavior-changing) / **동작보존**(behavior-preserving).

## 🐞 정합성 버그 (동작변경 — 테스트로 고정 후 수정)

| # | 위치 | 증상 | 영향 |
|---|------|------|------|
| B1 | `ecommerce/service/InventoryService.java` — `reserve()` / `confirm()` | 두 메서드가 **동일하게 재고를 차감**한다. `OrderService.placeOrder` 가 1단계(reserve)·5단계(confirm) 모두 호출 → **주문 1건당 재고 2배 차감**. | 높음 |
| B2 | `ecommerce/service/CartService.java` — `cartTotal()` | `total += unitPrice` 로 **수량을 무시**한다. 장바구니 합계가 과소 계산. | 중간 |
| B3 | `common-util/util/MoneyUtils.java` — `round()` | `Math.floor` 로 **버림**한다(주석은 "반올림 의도"라고 명시). 모든 금액 계산이 이 함수를 거친다. | 중간 |
| B4 | `ecommerce/service/CouponService.java` — `getValidCoupon()` | `!expiryDate.isAfter(today)` → **만료일 당일에 쿠폰이 거부**된다. `Coupon.expiryDate` 주석("만료일 당일 포함")과 모순(off-by-one). | 중간 |
| B5 | `core-framework/web/PageRequestDto.java` — `getOffset()` | `page * size` 인데 page가 1-based → 첫 페이지를 건너뛰는 오프셋 오류. | 중간 |
| B6 | `payment/service/RefundService.java` — `refund()` | 환불 누계가 결제액을 초과해도 막지 않는다. `ErrorCode.REFUND_EXCEEDS_PAYMENT`(PM002)가 정의돼 있으나 **한 번도 던져지지 않는다**(미사용 에러코드). 과다 환불 가능. | 높음 |
| B7 | `common-util/util/DateUtils.java` + `batch/job/DailySalesAggregationJob.java` | 주문 시각은 `now()`=**UTC** 로 저장되는데 집계는 `LocalDate.now()`=**서버 로컬**로 비교 → 자정 부근 날짜 경계에서 집계 누락/중복. | 중간 |

## ⚠️ 위험 · 안티패턴

| # | 위치 | 내용 | 수정 성격 |
|---|------|------|----------|
| R1 | `ecommerce/service/OrderService.java` — `placeOrder()` | God method. 재고/주문/쿠폰/가격/결제/장바구니/알림 7책임을 한 `@Transactional` 에서 처리. | 동작보존(추출 리팩토링) |
| R2 | `ecommerce/client/PaymentClient.java`, `admin/client/ShopGateway.java` | 서비스 간 HTTP를 **타입 DTO 없이 raw `Map`** 으로 주고받고 캐스팅. 계약이 코드에 드러나지 않음. [ADR-0005](./adr/0005-map-based-inter-service-http.md) | 동작보존 |
| R3 | `common-util/util/DateUtils.java` — static `SDF` | `SimpleDateFormat` 을 static 공유 → **thread-unsafe**. | 동작보존(→ `DateTimeFormatter`) |
| R4 | `core-framework/web/GlobalExceptionHandler.java` — `handleEtc()` | 모든 비즈니스 외 예외를 **로그 없이** 일괄 500(`C001`)으로 삼킴. 원인 추적 불가. | 대체로 동작보존(+로깅 추가) |
| R5 | 설정 전반 (`application.yml`, `@Value` 기본값) | 서비스 URL, `admin.token`=`admin-secret`, DB 경로가 **하드코딩**. 운영 분리 불가, 토큰 노출. | 동작보존(외부화) |
| R6 | `admin/util/AdminPriceCalculator.java` | `PricingService` 계산식을 **복붙**. 로직 이중 관리(한쪽만 고치면 불일치). | 동작보존(통합) |
| R7 | 엔티티 전반 (`Product.categoryId` 등) | FK 연관 없이 `Long` id만 보유 → 참조 무결성 없음, 고아 데이터 가능. | 동작변경(스키마 영향) |
| R8 | `config/RestTemplateConfig.java` | `new RestTemplate()` 에 **타임아웃 미설정**. 결제 서비스 지연 시 호출 스레드 무한 대기 위험. | 동작보존 |

## 🧹 정리

| # | 위치 | 내용 |
|---|------|------|
| C1 | `OrderService`, `BatchRunner`, 배치 잡들, `DataSeeder` 등 | `System.out.println` → SLF4J 로거로 교체. |
| C2 | `batch/job/*` | `findAll()` 후 Java에서 필터링(전체 스캔). 리포지토리 쿼리로 DB에서 거르도록. |
| C3 | `common-util` | `commons-lang3:3.12.0` 등 오래된 의존성 버전. |
| C4 | `DateUtils.parse()` | `ParseException` 을 삼키고 `null` 반환. 호출부 NPE 위험. |

## 대형 과제 (별도 프로젝트 단위)

- **금액 `double` → `BigDecimal` 전환**(B3 완화의 근본 해결): DTO·엔티티·DB 컬럼까지 파급. [ADR-0003](./adr/0003-money-as-double.md).
- **공유 H2 파일 DB 분리/전환**: ecommerce↔batch 공유 구조의 결합·동시성 한계. [ADR-0002](./adr/0002-shared-h2-file-database.md).

## 권장 처리 순서 (향후 리팩토링 패스)

1. ~~**검증 루프 구축** — 테스트 인프라 + 위 버그들의 현재 동작을 고정하는 characterization 테스트.~~
   ✅ **완료** (2026-06-12): `common-util`/`ecommerce-service`/`payment-service`에 28개 테스트. B1·B2·B3·B4·B6의
   현재 동작이 고정되어 있다. 이제 아래 수정은 해당 단언을 같은 커밋에서 뒤집으며 진행한다.
2. **고영향·저위험 버그**: B1, B2, B3, B4, B6 (테스트의 단언을 같은 커밋에서 뒤집어 의도를 드러낸다).
3. **동작보존 정리**: C1(로깅), R4(예외 로깅), R8(타임아웃), R6(중복 제거).
4. **구조 리팩토링**: R1(God method 추출), R2(Map→DTO).
5. **대형 과제**: BigDecimal 전환, DB 구조, 설정 외부화(R5), B7 타임존 정리.
