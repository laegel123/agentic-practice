# CLAUDE.md — legacy-shop

이 코드베이스에서 작업하는 에이전트를 위한 운영 가이드. 상세 문서는 [`docs/`](./docs/) 참고.

## 무엇인가

Java 21 / Spring Boot 3.5.15 기반 **사내 커머스 백엔드**. Gradle 멀티모듈 = **모듈형 모놀리스**
(독립 실행 앱 4개 + 공유 라이브러리 2개). 원 담당자 퇴사로 방치되었던 레거시 코드이며,
정합성 버그·안티패턴이 다수 존재한다(의도 vs 부채 구분은 [`docs/known-issues.md`](./docs/known-issues.md)).

| 모듈 | 역할 | 포트 |
|------|------|------|
| `common-util` | 정적 유틸(금액/날짜/문자열/암호/JSON) — 순수 Java | — |
| `core-framework` | 공통 기반(ApiResponse, ErrorCode, BusinessException, BaseTimeEntity, GlobalExceptionHandler) | — |
| `ecommerce-service` | 상품/재고/장바구니/주문/쿠폰/고객. **DB 스키마 소유자** | 8081 |
| `payment-service` | 결제/환불/원장 | 8082 |
| `admin` | 관리자 API(자체 DB 없음, 서비스를 HTTP 호출) | 8083 |
| `batch` | 정산/집계/재고대사/장바구니정리. 1회 실행 후 종료 | — |

전체 그림: [`docs/architecture.md`](./docs/architecture.md).

## 빌드 / 실행 / 테스트

> Windows PowerShell(기본 셸)에서는 **`.\gradlew.bat`**, Bash 도구에서는 **`./gradlew`**.

```powershell
.\gradlew.bat build                        # 전체 빌드
.\gradlew.bat test                         # 전체 테스트 (67개: characterization + 버그수정 회귀 B1·B2·B3·B4·B5·B6·B7·BT1 + E1·A1·CU1 보안 회귀 + 동작보존 정리 회귀 R4·R6·CU2 + 구조 리팩토링 회귀 R2 PaymentClientTest)
.\gradlew.bat :ecommerce-service:test      # 모듈 단위 테스트
.\gradlew.bat :ecommerce-service:bootRun   # :8081 (가장 먼저 — 스키마 생성)
.\gradlew.bat :payment-service:bootRun     # :8082
.\gradlew.bat :admin:bootRun               # :8083 (ecommerce/payment 기동 후)
.\gradlew.bat :batch:bootRun               # 잡 1회 실행 후 종료
```

권장 기동 순서: **ecommerce → payment → (admin / batch)**.

## DB

H2 파일 DB, 물리 2개. user `sa`, 비밀번호 없음. 각 웹 앱 `/h2-console`.
- `~/legacyshopdb` — ecommerce(스키마 생성, `ddl-auto:update`) + batch(공유, `ddl-auto:none`)
- `~/legacypaydb` — payment 전용

## 최초 설정 (1회) — git 훅

`pre-push` 훅이 `legacy-ecommerce/.githooks/` 에 있다 — (1) `main` 강제 푸쉬/삭제 차단,
(2) 푸쉬 전 `./gradlew test` 실행(실패 시 푸쉬 중단). 클론마다 한 번 연결해야 한다(저장소 루트에서):

```bash
git config core.hooksPath legacy-ecommerce/.githooks
```

상세·우회·GitHub 브랜치 보호 권장은 [`.githooks/README.md`](./.githooks/README.md) 참고.

## 컨벤션 (요약 — 상세는 [docs/code-conventions.md](./docs/code-conventions.md))

- 패키지: `com.legacy.shop.<module>.{web,service,domain,dto,repository,client,config}`.
- **생성자 주입만** 사용(`private final`, `@Autowired` 금지). **Lombok 없음** — getter/setter 수기 작성.
- 엔티티는 `BaseTimeEntity` 상속, `@GeneratedValue(IDENTITY)`, enum은 `@Enumerated(STRING)`.
- 응답은 `ApiResponse<T>`(성공 `code="0000"`). 오류는 `throw new BusinessException(ErrorCode.X)` →
  새 에러는 `core-framework` 의 단일 `ErrorCode` enum에 추가.
- DTO는 Java `record`. `common-util` 의 정적 유틸을 재사용(새로 만들지 말 것).
- 버전 카탈로그 미사용 — Spring 의존성은 BOM이 관리하므로 버전 문자열 없이 선언.

## ⚠️ 함정 — 무심코 "고치지" 말 것

아래는 **알려진 버그/부채**다. **현재 동작을 고정하는 characterization 테스트가 이미 작성되어
있으므로**(`*/src/test`), 이 중 하나를 고칠 때는 동작이 바뀐다는 뜻이고 해당 테스트의 단언을
**같은 커밋에서 의도적으로 뒤집어야** 한다. 무심코 "초록색 만들기"로 약화시키지 말 것.

- `DateUtils`: static `SimpleDateFormat`(thread-unsafe, R3), `parse()` null 삼킴(C4). (`now()`=UTC vs `localToday()`=로컬 혼용 B7 은 ✅ 수정 — 아래 참조.)
- 서비스 URL/`admin.token` 하드코딩(R5). (서비스 간 HTTP raw `Map` R2·`RestTemplate` 타임아웃 미설정 R8 은 ✅ 수정 — 아래 참조.)
- ✅ **고영향 버그 B1·B2·B3·B4·B5·B6·B7·BT1 수정 완료**(따라서 **모방 금지 대상 아님**): `InventoryService.confirm()` 재고 이중차감 B1(→ confirm 비차감) · `CartService.cartTotal()` 수량 무시 B2(→ `MoneyUtils.multiply` 합산) · `MoneyUtils.round()` 버림 B3(→ `BigDecimal` HALF_UP 반올림) · `CouponService` 만료 당일 거부 B4(→ `isBefore`, 당일 포함) · `PageRequestDto.getOffset()` 페이지 오프셋 B5(→ `(page-1)*size`, 첫 페이지 offset 0) · `RefundService` 과다 환불 B6(→ `REFUND_EXCEEDS_PAYMENT` throw) · 집계 타임존 B7(→ UTC `Clock` 주입으로 집계 '오늘'을 주문 시각(UTC)과 통일) · batch 취소주문 매출 합산 BT1(→ `CANCELLED` 필터) — 모두 ✅ 2026-06-16 수정. (`admin/AdminPriceCalculator` 의 `Math.floor` 복붙 R6 으로 인한 B3 분기도 ✅ 해소 — 아래 동작보존 정리 참조.)
- ✅ **보안 최우선 3건 모두 수정 완료**(따라서 **모방 금지 대상 아님**): `ProductSearchDao` SQL 인젝션 E1 · `POST /admin/refunds` 무인증 A1 · `CryptoUtils` MD5·무 salt 비밀번호 해시 CU1(→ PBKDF2+임의 salt, 레거시 MD5 검증 폴백) — 모두 ✅ 2026-06-15 수정.
- ✅ **동작보존 정리 5건 모두 수정 완료**(따라서 **모방 금지 대상 아님**): C1(`System.out.println` 9곳 → SLF4J) · R4(`GlobalExceptionHandler.handleEtc` 로깅 추가 + `ErrorCode.INTERNAL_ERROR`, 응답 동일) · R8(`RestTemplateConfig` 양쪽 connect 2s/read 5s 타임아웃) · R6(`AdminPriceCalculator` → `MoneyUtils` 위임, B3 floor 분기 해소) · CU2(`JsonUtils` fail-fast `JsonException` 통일) — 모두 ✅ 2026-06-16 수정.
- ✅ **구조 리팩토링 3건 모두 완료**(따라서 **모방 금지 대상 아님**): R1(`OrderService.placeOrder` God method → 7단계 private 메서드 추출, 동작보존) · R2(`PaymentClient`/`ShopGateway` raw `Map` → 타입 record, charge 응답 캐스팅 제거) · BT2(`OrderStatus` 복제 2개 → `core-framework` `com.legacy.shop.core.domain.OrderStatus` 단일 정의로 통일) — 모두 ✅ 2026-06-16 수정.

전체 목록·우선순위: [`docs/known-issues.md`](./docs/known-issues.md). 결정 배경: [`docs/adr/`](./docs/adr/).

## 현재 상태

- **검증 루프 구축 완료**: `common-util`/`core-framework`/`ecommerce-service`/`payment-service`/`admin`/`batch`에 JUnit5 + Mockito +
  AssertJ 기반 테스트 67개(`.\gradlew.bat test`, 전부 green) — characterization 28개 + 버그수정 회귀 17개
  (B1 `InventoryServiceTest` 5 + BT1·B7 batch `SettlementJobTest`·`DailySalesAggregationJobTest` 5 + B5 `PageRequestDtoTest` 4·`ProductServiceTest` 3;
  B2·B3·B4·B6 는 기존 characterization 단언을 뒤집어 흡수) +
  보안 회귀 12개(E1 `ProductSearchDaoTest` 3 + A1 `AdminRefundControllerTest` 3 + CU1 `CryptoUtilsTest` 6) +
  동작보존 정리 회귀 8개(R4 `GlobalExceptionHandlerTest` 2 + R6 `AdminPriceCalculatorTest` 3 + CU2 `JsonUtilsTest` 3) +
  구조 리팩토링 회귀 2개(R2 `PaymentClientTest` 2; R1·BT2 는 기존 테스트가 안전망).
  향후 리팩토링의 안전망 역할을 한다. 테스트는 인메모리 H2 프로파일(`test`)로 실 DB와 격리됨.
- **고영향·정합성 버그 8건 전부 수정 완료(B1·B6 → B2·B3·B4·BT1 → B5·B7, 모두 2026-06-16)**: B1(재고 이중차감, `confirm` 비차감) ·
  B2(장바구니 수량 무시 → `MoneyUtils.multiply` 합산) · B3(`MoneyUtils.round` 버림 → `BigDecimal` HALF_UP 반올림) ·
  B4(쿠폰 만료 당일 거부 → `isBefore`, 당일 포함) · B5(페이지 오프셋 → `(page-1)*size`, core-framework 첫 테스트) ·
  B6(과다환불 → 한도 검증 후 `REFUND_EXCEEDS_PAYMENT`) · B7(집계 타임존 → UTC `Clock` 주입으로 집계 '오늘'을 주문 시각(UTC)과 통일) ·
  BT1(batch 취소주문 매출 합산 → `CANCELLED` 필터, batch 모듈 첫 테스트). 각각 같은 커밋에서 단언을 뒤집거나 신규 회귀 추가.
- **보안 최우선 3건 수정 완료**: E1(SQL 인젝션)·A1(무인증 환불)·CU1(MD5 해시) ✅ 완료(2026-06-15) — E1 `ProductSearchDao`
  파라미터 바인딩, A1 `AdminRefundController` 에 `X-Admin-Token` 검사, CU1 `CryptoUtils` 를 PBKDF2(임의 salt)+레거시
  MD5 검증 폴백으로 교체(각각 회귀 테스트 동반).
- **동작보존 정리 5건 수정 완료(2026-06-16)**: C1(`System.out.println` 9곳 → SLF4J) · R4(`handleEtc` 스택트레이스 로깅 + `ErrorCode.INTERNAL_ERROR`, 응답 동일) · R8(양쪽 `RestTemplateConfig` connect 2s/read 5s 타임아웃) · R6(`AdminPriceCalculator` → `MoneyUtils` 위임, B3 floor 분기 해소) · CU2(`JsonUtils` fail-fast `JsonException` 통일). R4·R6·CU2 는 신규 회귀 테스트 동반(C1·R8 은 동작 보존이라 기존 테스트가 안전망).
- **구조 리팩토링 3건 수정 완료(2026-06-16)**: R1(`OrderService.placeOrder` God method → 7단계 private 메서드 추출, 동작보존 — `OrderServiceTest` 안전망) · R2(`PaymentClient` 요청·응답 + `ShopGateway` 환불 요청 raw `Map` → 타입 record, charge 응답 캐스팅 제거; 패스스루 응답은 무상태 프록시 특성상 유지 — `PaymentClientTest` 와이어 계약) · BT2(`OrderStatus` 복제 2개 → `core-framework` `com.legacy.shop.core.domain.OrderStatus` 단일 정의 통일, DB 값 불변).
- 다음 과제: **정합성·고영향 버그·동작보존 정리·구조 리팩토링 전부 완료**. 다음은 **대형 과제**: 금액 `double`→`BigDecimal` 전환, 공유 H2 DB 분리, 설정 외부화(R5). [`docs/known-issues.md`](./docs/known-issues.md) "권장 처리 순서" 6단계 참고.
- 문서는 "코드가 실제로 하는 일"을 기술한다. README의 포트(8080)는 구버전 오기이며 실제는 **8081**.
