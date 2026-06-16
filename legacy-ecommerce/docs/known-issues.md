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
| B1 | `ecommerce/service/InventoryService.java` — `confirm()` | ✅ **수정됨(2026-06-16).** (이전) `confirm()` 이 `reserve()` 와 동일하게 재고를 또 차감 → `OrderService.placeOrder` 가 reserve(1단계)·confirm(5단계) 모두 호출해 **주문 1건당 재고 2배 차감**. → **`confirm()` 은 더 이상 차감하지 않고 존재 검증만 수행**(reserve 단계에서 이미 차감). 회귀 테스트 `InventoryServiceTest`(5개: reserve 차감·confirm 불변·reserve→confirm 단일차감·미존재 예외·restore 복원). | 높음 |
| B2 | `ecommerce/service/CartService.java` — `cartTotal()` | ✅ **수정됨(2026-06-16).** (이전) `total += unitPrice` 로 수량을 무시해 합계 과소 계산 → **`MoneyUtils.multiply(unitPrice, quantity)` 합산**으로 교체. 회귀: `CartServiceTest` 단언 30→80 으로 뒤집음. | 중간 |
| B3 | `common-util/util/MoneyUtils.java` — `round()` | ✅ **수정됨(2026-06-16).** (이전) `Math.floor` 버림(주석은 "반올림 의도") → **`BigDecimal.setScale(2, HALF_UP)` 반올림**으로 교체(새 의존성 0개). 회귀: `MoneyUtilsTest` 의 버림 단언을 반올림으로 뒤집음(셋째자리·`taxOf(99.99)→10.00`+경계 0.005/0.004). `PricingService` 산출값은 전부 정확히 떨어져 영향 없음. ⚠ `admin/AdminPriceCalculator`(R6 복붙)는 여전히 floor — 분기 확대(아래 R6). | 중간 |
| B4 | `ecommerce/service/CouponService.java` — `getValidCoupon()` | ✅ **수정됨(2026-06-16).** (이전) `!expiryDate.isAfter(today)` 로 만료일 당일 거부(off-by-one) → **`expiryDate.isBefore(today)`** 로 교체(만료일 당일 포함 유효, `Coupon.expiryDate` 주석과 일치). 회귀: `CouponServiceTest` 의 당일 거부 단언을 유효로 뒤집음. | 중간 |
| B5 | `core-framework/web/PageRequestDto.java` — `getOffset()` | ✅ **수정됨(2026-06-16).** (이전) `page * size` 인데 page가 1-based → 첫 페이지를 통째로 건너뜀 → **`(page-1)*size`** 로 교체(첫 페이지 offset 0). 회귀: `PageRequestDtoTest`(core-framework 첫 테스트, 4개) + `ProductServiceTest`(첫 페이지 반환, 3개). | 중간 |
| B6 | `payment/service/RefundService.java` — `refund()` | ✅ **수정됨(2026-06-16).** (이전) 환불 누계가 결제액을 초과해도 막지 않아 과다 환불 가능(`REFUND_EXCEEDS_PAYMENT` PM002 정의만 되고 미사용). → **환불 전 `기존 누계 + 이번 환불 > 결제액` 이면 `BusinessException(REFUND_EXCEEDS_PAYMENT)` throw**(환불/원장 미기록). 회귀 테스트 `RefundServiceTest.overRefund_isBlocked_throwsRefundExceedsPayment`(단언을 허용→차단으로 뒤집음). | 높음 |
| B7 | `common-util/util/DateUtils.java` + `batch/job/DailySalesAggregationJob.java` | ✅ **수정됨(2026-06-16).** (이전) 주문 시각은 `now()`=**UTC** 로 저장되는데 집계는 `LocalDate.now()`=**서버 로컬**로 비교 → 자정 부근 날짜 경계에서 집계 누락/중복 → **집계 '오늘'을 주입된 `Clock`(기본 `Clock.systemUTC()`) 기준 UTC 날짜로 통일**(주문 시각과 같은 기준). `BatchApplication` 에 `@Bean Clock` 추가, `DailySalesAggregationJob` 생성자에 `Clock` 주입(단일 생성자 유지). `DateUtils.localToday()` 코드는 유지(쿠폰 만료 등 달력 날짜용)하되 주석에 "UTC 주문 시각 집계에 쓰지 말 것" 명시. 회귀: `DailySalesAggregationJobTest` 에 고정 Clock 기반 UTC 일경계 테스트 추가. | 중간 |

## ⚠️ 위험 · 안티패턴

| # | 위치 | 내용 | 수정 성격 |
|---|------|------|----------|
| R1 | `ecommerce/service/OrderService.java` — `placeOrder()` | God method. 재고/주문/쿠폰/가격/결제/장바구니/알림 7책임을 한 `@Transactional` 에서 처리. | 동작보존(추출 리팩토링) |
| R2 | `ecommerce/client/PaymentClient.java`, `admin/client/ShopGateway.java` | 서비스 간 HTTP를 **타입 DTO 없이 raw `Map`** 으로 주고받고 캐스팅. 계약이 코드에 드러나지 않음. [ADR-0005](./adr/0005-map-based-inter-service-http.md) | 동작보존 |
| R3 | `common-util/util/DateUtils.java` — static `SDF` | `SimpleDateFormat` 을 static 공유 → **thread-unsafe**. | 동작보존(→ `DateTimeFormatter`) |
| R4 | `core-framework/web/GlobalExceptionHandler.java` — `handleEtc()` | ✅ **수정됨(2026-06-16).** (이전) 모든 비즈니스 외 예외를 **로그 없이** 일괄 500(`C001`)으로 삼켜 원인 추적 불가 → **SLF4J 로 스택트레이스 로깅 추가 + 하드코딩 `"C001"` → `ErrorCode.INTERNAL_ERROR`**. `INTERNAL_ERROR`=(500,`C001`,동일 메시지)라 **응답은 종전과 동일**(동작 보존). 회귀 테스트 `GlobalExceptionHandlerTest`(순수 단위, handleEtc 500/`C001` 고정 + handleBusiness 매핑). | 대체로 동작보존(+로깅 추가) — 완료 |
| R5 | 설정 전반 (`application.yml`, `@Value` 기본값) | 서비스 URL, `admin.token`=`admin-secret`, DB 경로가 **하드코딩**. 운영 분리 불가, 토큰 노출. | 동작보존(외부화) |
| R6 | `admin/util/AdminPriceCalculator.java` | ✅ **수정됨(2026-06-16).** (이전) `PricingService` 계산식을 **복붙**해 로직 이중 관리 + B3 이후 분기 확대(복붙본만 `Math.floor` 버림 → 미리보기/실제 ±0.01 어긋남). → **자체 산식·`TAX_RATE` 상수를 제거하고 공용 `MoneyUtils.taxOf`/`MoneyUtils.round`(HALF_UP) 로 위임**(PricingService 와 같은 세금=소계×세율 반올림, 합계=소계+세금-할인 반올림 규칙 공유) → admin 미리보기도 HALF_UP 반올림이 되어 ecommerce 와 일치(B3 분기 해소). 회귀 테스트 `AdminPriceCalculatorTest`(반올림 적용·할인 반영·MoneyUtils 규칙 일치). | 동작보존(통합; admin 미리보기 floor→round) — 완료 |
| R7 | 엔티티 전반 (`Product.categoryId` 등) | FK 연관 없이 `Long` id만 보유 → 참조 무결성 없음, 고아 데이터 가능. | 동작변경(스키마 영향) |
| R8 | `config/RestTemplateConfig.java` (ecommerce·admin) | ✅ **수정됨(2026-06-16).** (이전) `new RestTemplate()` 에 **타임아웃 미설정** → 다운스트림 지연 시 호출 스레드 무한 대기 위험 → **`SimpleClientHttpRequestFactory` 로 connect 2s / read 5s 타임아웃 설정**(ecommerce `PaymentClient`·admin `ShopGateway` 양쪽 빈). 행 호출이 무한 대기 대신 타임아웃으로 끊긴다(정상 응답 동작은 보존). | 동작보존 — 완료 |

## 🧹 정리

| # | 위치 | 내용 |
|---|------|------|
| C1 | `OrderService`, `BatchRunner`, 배치 잡들, `DataSeeder` 등 | ✅ **수정됨(2026-06-16).** `System.out.println` 9곳을 SLF4J 로거(`log.info`/`log.error`, `{}` 파라미터화)로 교체 — `OrderService`·`DataSeeder`(ecommerce), `BatchRunner`·`SettlementJob`·`DailySalesAggregationJob`·`AbandonedCartCleanupJob`·`InventoryReconciliationJob`(batch). 동작 보존(출력처만 stdout→로거). |
| C2 | `batch/job/*` | `findAll()` 후 Java에서 필터링(전체 스캔). 리포지토리 쿼리로 DB에서 거르도록. |
| C3 | `common-util` | `commons-lang3:3.12.0` 등 오래된 의존성 버전. |
| C4 | `DateUtils.parse()` | `ParseException` 을 삼키고 `null` 반환. 호출부 NPE 위험. |

## 🔒 모듈 한정 발견 (모듈 탐색에서 승격 — 코드 유지)

각 모듈 탐색에서 나와 기존엔 모듈 문서에만 있던 항목을 이 단일 백로그로 통합했다. 코드(A/BT/CU)는
다른 문서의 참조 호환을 위해 그대로 둔다. 보안 3건 **E1·A1·CU1 모두 ✅ 완료(2026-06-15)**.

| # | 위치 | 증상 | 영향 | 수정 성격 |
|---|------|------|------|----------|
| E1 | `ecommerce/repository/ProductSearchDao.java` — `searchByName()` | ✅ **수정됨(2026-06-15).** (이전) 검색어를 native SQL 에 문자열로 직접 이어붙여 `GET /api/products/search` 의 `keyword` 로 임의 SQL 주입 가능 → **named parameter 바인딩(`:keyword`)으로 교체**. 회귀 테스트 `ProductSearchDaoTest`(정상 검색 보존 + 인젝션 페이로드 차단). | 높음(보안) | 동작보존(파라미터 바인딩) — 완료 |
| A1 | `admin/web/AdminRefundController.java` | ✅ **수정됨(2026-06-15).** (이전) `POST /admin/refunds` 가 다른 admin 컨트롤러와 달리 `AdminAuth` 를 주입조차 안 해 무인증으로 환불 트리거 가능 → **`AdminAuth` 주입 + `X-Admin-Token` 검사**를 다른 컨트롤러와 동일하게 추가. 회귀 테스트 `AdminRefundControllerTest`(무토큰·오토큰 401 + 게이트웨이 미호출, 유효토큰 성공) — admin 모듈 첫 테스트. | 높음(보안) | 동작변경(인증 추가) — 완료 |
| CU1 | `common-util/util/CryptoUtils.java` — `hashPassword()` | ✅ **수정됨(2026-06-15).** (이전) MD5 + 무 salt 비밀번호 해시 → 레인보우테이블·동일비번 노출 → **JDK 내장 PBKDF2(HMAC-SHA256, 매번 임의 salt, self-describing `pbkdf2$iter$salt$hash`)로 교체**. 새 의존성 0개(모듈 "순수 Java" 정체성 유지). MD5 는 평문 복구 불가라 일괄 변환이 불가능 → `verifyPassword` 가 PBKDF2·레거시 MD5 **두 형식을 모두 검증**(투명/점진 마이그레이션), `needsRehash` 로 로그인 시 재해시(upgrade-on-login) 가능. 회귀 테스트 `CryptoUtilsTest`(6개: 형식·salt 비결정성·검증·레거시 폴백·needsRehash·null 안전). | 높음(보안) | 동작변경(해시 교체 + 점진 마이그레이션) — 완료 |
| BT1 | `batch/job/SettlementJob.java`, `DailySalesAggregationJob.java` | ✅ **수정됨(2026-06-16).** (이전) `OrderRow.status` 를 매핑만 하고 필터에 안 써 `CANCELLED` 도 합산 → 매출 과대계상 → **두 잡 모두 `status == CANCELLED` 행을 합산에서 제외**. `DailySalesAggregationJob.aggregate()` 는 테스트 관측을 위해 `DailySales(count, revenue)` record 반환(읽기 전용 유지). **batch 모듈 첫 테스트 인프라**(`spring-boot-starter-test`) + `SettlementJobTest`·`DailySalesAggregationJobTest`(각 2개, `ReflectionTestUtils` 로 read-only `OrderRow` 픽스처). B7(타임존)은 범위 밖이라 유지. | 높음 | 동작변경(상태 필터) — 완료 |
| BT2 | `batch/domain/OrderStatus.java` | ⚠️ **enum 복제 드리프트.** 공유 DB 라 ecommerce enum 을 import 못 해 재정의. ecommerce 가 상태 추가 시 batch 는 모르고, 미지값 읽으면 Hibernate 예외. | 중간 | 동작보존(동기화) |
| CU2 | `common-util/util/JsonUtils.java` | ✅ **수정됨(2026-06-16).** (이전) `toJson` 은 예외 삼키고 `null`, `fromJson` 은 `RuntimeException` → 호출부 일관 처리 불가 → **fail-fast 로 통일**: 둘 다 실패 시 신규 `JsonException`(RuntimeException 하위) throw. `toJson` 의 null 반환 제거(코드 내 호출부 0건이라 파급 없음). 회귀 테스트 `JsonUtilsTest`(라운드트립·toJson 순환참조 throw·fromJson 파싱오류 throw). | 중간 | 동작보존(정책 통일; toJson null→throw) — 완료 |
| CU3 | `common-util/util/ValidationUtils.java` | ⚠️ **정규식 과허용/지역 고정.** `EMAIL` 단순·과허용, `PHONE` 한국 `01x` 전용(국제·유선 불통과). | 낮음 | 동작변경(정규식 강화) |

> batch `AbandonedCartCleanupJob` 은 이름이 "cleanup" 이지만 삭제 없이 건수만 리포트한다 — **의도된 현재
> 동작**이라 백로그가 아니라 주의점이다(batch [`CLAUDE.md`](../batch/CLAUDE.md) 참고). 무심코 삭제 로직을 넣지 말 것.

## 대형 과제 (별도 프로젝트 단위)

- **금액 `double` → `BigDecimal` 전환**(B3 완화의 근본 해결): DTO·엔티티·DB 컬럼까지 파급. [ADR-0003](./adr/0003-money-as-double.md).
- **공유 H2 파일 DB 분리/전환**: ecommerce↔batch 공유 구조의 결합·동시성 한계. [ADR-0002](./adr/0002-shared-h2-file-database.md).

## 권장 처리 순서 (향후 리팩토링 패스)

1. ~~**검증 루프 구축** — 테스트 인프라 + 위 버그들의 현재 동작을 고정하는 characterization 테스트.~~
   ✅ **완료** (2026-06-12): `common-util`/`ecommerce-service`/`payment-service`에 28개 테스트. B1·B2·B3·B4·B6의
   현재 동작이 고정되어 있다. 이제 아래 수정은 해당 단언을 같은 커밋에서 뒤집으며 진행한다.
2. **보안 최우선** (모듈 발견) — **3건 모두 ✅ 완료(2026-06-15)**: ~~E1(SQL 인젝션)~~ 파라미터 바인딩 + `ProductSearchDaoTest`. ~~A1(무인증 환불)~~ `AdminAuth` 주입 + `X-Admin-Token` 검사 + `AdminRefundControllerTest`. ~~CU1(MD5 해시)~~ PBKDF2(임의 salt)+레거시 MD5 검증 폴백(점진 마이그레이션) + `CryptoUtilsTest`.
3. **고영향·저위험 버그 — 전부 ✅ 완료(2026-06-16)**: ~~B1(재고 이중차감)~~ `confirm` 비차감 + `InventoryServiceTest`, ~~B6(과다환불)~~ 한도 검증 + `RefundServiceTest` 단언 뒤집음, ~~B2(장바구니 수량무시)~~ `MoneyUtils.multiply` 합산 + `CartServiceTest` 단언 뒤집음, ~~B3(round 버림)~~ `BigDecimal` HALF_UP + `MoneyUtilsTest` 단언 뒤집음, ~~B4(쿠폰 off-by-one)~~ `isBefore` + `CouponServiceTest` 단언 뒤집음, ~~BT1(취소주문 매출)~~ `CANCELLED` 필터 + batch 첫 테스트(`SettlementJobTest`·`DailySalesAggregationJobTest`), ~~B5(페이지 오프셋)~~ `(page-1)*size` + `PageRequestDtoTest`(core-framework 첫 테스트)·`ProductServiceTest`, ~~B7(집계 타임존)~~ UTC `Clock` 주입으로 집계 기준 통일 + `DailySalesAggregationJobTest` UTC 일경계 회귀. **→ 정합성 버그·고영향 버그 전부 완료.**
4. **동작보존 정리 — 전부 ✅ 완료(2026-06-16)**: ~~C1(로깅)~~ `System.out.println` 9곳 → SLF4J, ~~R4(예외 로깅)~~ `handleEtc` 스택트레이스 로깅 + `ErrorCode.INTERNAL_ERROR`(응답 동일) + `GlobalExceptionHandlerTest`, ~~R8(타임아웃)~~ 양쪽 `RestTemplateConfig` 에 connect 2s/read 5s, ~~R6(중복 제거)~~ `AdminPriceCalculator` → `MoneyUtils` 위임(B3 floor 분기 해소) + `AdminPriceCalculatorTest`, ~~CU2(JSON 오류처리)~~ `JsonUtils` fail-fast `JsonException` 통일 + `JsonUtilsTest`. **→ 동작보존 정리 전부 완료.**
5. **구조 리팩토링**: R1(God method 추출), R2(Map→DTO), BT2(enum 동기화). ← **다음 차례**
6. **대형 과제**: BigDecimal 전환, DB 구조, 설정 외부화(R5). (B7 타임존은 ✅ 3단계에서 완료.)
