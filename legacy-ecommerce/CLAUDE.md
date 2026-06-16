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
.\gradlew.bat test                         # 전체 테스트 (49개: characterization + 버그수정 회귀 B1·B2·B3·B4·B6·BT1 + E1·A1·CU1 보안 회귀)
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

- `PageRequestDto.getOffset()` 오프셋 오류(B5). `DateUtils`: `now()`=UTC vs `localToday()`=서버로컬 혼용(B7), static `SimpleDateFormat`(thread-unsafe).
- `GlobalExceptionHandler` 가 예외를 로그 없이 삼킴. 곳곳에 `System.out.println`.
- 서비스 간 HTTP가 raw `Map`(타입 없음). 서비스 URL/`admin.token` 하드코딩.
- ✅ **고영향 버그 B1·B2·B3·B4·B6·BT1 수정 완료**(따라서 **모방 금지 대상 아님**): `InventoryService.confirm()` 재고 이중차감 B1(→ confirm 비차감) · `CartService.cartTotal()` 수량 무시 B2(→ `MoneyUtils.multiply` 합산) · `MoneyUtils.round()` 버림 B3(→ `BigDecimal` HALF_UP 반올림) · `CouponService` 만료 당일 거부 B4(→ `isBefore`, 당일 포함) · `RefundService` 과다 환불 B6(→ `REFUND_EXCEEDS_PAYMENT` throw) · batch 취소주문 매출 합산 BT1(→ `CANCELLED` 필터) — 모두 ✅ 2026-06-16 수정. ⚠ 단, `admin/AdminPriceCalculator` 는 `MoneyUtils` 를 안 쓰고 자체 `Math.floor` 복붙(R6)이라 **B3 반올림이 반영 안 됨** — 미리보기/실제 ±0.01 분기, R6 통합 시 해소.
- ✅ **보안 최우선 3건 모두 수정 완료**(따라서 **모방 금지 대상 아님**): `ProductSearchDao` SQL 인젝션 E1 · `POST /admin/refunds` 무인증 A1 · `CryptoUtils` MD5·무 salt 비밀번호 해시 CU1(→ PBKDF2+임의 salt, 레거시 MD5 검증 폴백) — 모두 ✅ 2026-06-15 수정.

전체 목록·우선순위: [`docs/known-issues.md`](./docs/known-issues.md). 결정 배경: [`docs/adr/`](./docs/adr/).

## 현재 상태

- **검증 루프 구축 완료**: `common-util`/`ecommerce-service`/`payment-service`/`admin`/`batch`에 JUnit5 + Mockito +
  AssertJ 기반 테스트 49개(`.\gradlew.bat test`, 전부 green) — characterization 28개 + 버그수정 회귀 9개
  (B1 `InventoryServiceTest` 5 + BT1 `SettlementJobTest`·`DailySalesAggregationJobTest` 4; B2·B3·B4·B6 는 기존 characterization 단언을 뒤집어 흡수) +
  보안 회귀 12개(E1 `ProductSearchDaoTest` 3 + A1 `AdminRefundControllerTest` 3 + CU1 `CryptoUtilsTest` 6).
  향후 리팩토링의 안전망 역할을 한다. 테스트는 인메모리 H2 프로파일(`test`)로 실 DB와 격리됨.
- **고영향 버그 6건 수정 완료(B1·B6 2026-06-16 → B2·B3·B4·BT1 2026-06-16)**: B1(재고 이중차감, `confirm` 비차감) ·
  B2(장바구니 수량 무시 → `MoneyUtils.multiply` 합산) · B3(`MoneyUtils.round` 버림 → `BigDecimal` HALF_UP 반올림) ·
  B4(쿠폰 만료 당일 거부 → `isBefore`, 당일 포함) · B6(과다환불 → 한도 검증 후 `REFUND_EXCEEDS_PAYMENT`) ·
  BT1(batch 취소주문 매출 합산 → `CANCELLED` 필터, batch 모듈 첫 테스트). 각각 같은 커밋에서 단언을 뒤집거나 신규 회귀 추가.
- **보안 최우선 3건 수정 완료**: E1(SQL 인젝션)·A1(무인증 환불)·CU1(MD5 해시) ✅ 완료(2026-06-15) — E1 `ProductSearchDao`
  파라미터 바인딩, A1 `AdminRefundController` 에 `X-Admin-Token` 검사, CU1 `CryptoUtils` 를 PBKDF2(임의 salt)+레거시
  MD5 검증 폴백으로 교체(각각 회귀 테스트 동반).
- 다음 과제: 남은 버그 B5(페이지 오프셋)·B7(집계 타임존), 동작보존 정리(C1 로깅·R4·R8·R6 중복제거), 구조 리팩토링(R1 God method·R2 Map→DTO). [`docs/known-issues.md`](./docs/known-issues.md) "권장 처리 순서" 참고.
- 문서는 "코드가 실제로 하는 일"을 기술한다. README의 포트(8080)는 구버전 오기이며 실제는 **8081**.
