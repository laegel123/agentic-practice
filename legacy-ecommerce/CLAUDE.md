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

전체 그림: [`docs/architecture.md`](./docs/architecture.md). (README 의 포트 8080 은 구버전 오기 — 실제는 **8081**.)

## 빌드 / 실행 / 테스트

> Windows PowerShell(기본 셸)에서는 **`.\gradlew.bat`**, Bash 도구에서는 **`./gradlew`**.

```powershell
.\gradlew.bat build                        # 전체 빌드
.\gradlew.bat test                         # 전체 테스트 (전부 green — characterization + 버그수정·보안·리팩토링 회귀)
.\gradlew.bat :ecommerce-service:test      # 모듈 단위 테스트
.\gradlew.bat :ecommerce-service:bootRun   # :8081 (가장 먼저 — 스키마 생성)
.\gradlew.bat :payment-service:bootRun     # :8082
.\gradlew.bat :admin:bootRun               # :8083 (ecommerce/payment 기동 후)
.\gradlew.bat :batch:bootRun               # 잡 1회 실행 후 종료
```

권장 기동 순서: **ecommerce → payment → (admin / batch)**.

> 테스트는 **현재 동작을 고정하는 characterization 테스트**다(JUnit5 + Mockito + AssertJ, 인메모리 H2
> `test` 프로파일로 실 DB 격리). 동작이 바뀌는 수정은 해당 단언을 **같은 커밋에서 의도적으로 뒤집어야**
> 한다 — "초록색 만들기"로 약화시키지 말 것.

## DB

H2 파일 DB, 물리 2개. user `sa`, 비밀번호 없음. 각 웹 앱 `/h2-console`.
- `~/legacyshopdb` — ecommerce(스키마 생성, `ddl-auto:update`) + batch(공유, `ddl-auto:none`)
- `~/legacypaydb` — payment 전용

접속정보·서비스 URL 은 환경변수로 외부 주입한다(미설정 시 위 로컬 기본값 — [ADR-0007](./docs/adr/0007-config-via-environment-variables.md)에 키 목록).
`SHOP_DB_*`(ecommerce·batch 공유) · `PAYMENT_DB_*` · `ECOMMERCE_BASE_URL` · `PAYMENT_BASE_URL` · `ADMIN_TOKEN`(fail-closed).

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
- **금액은 `BigDecimal`(scale 2/HALF_UP)** 로 다루고 `MoneyUtils` 를 거친다. 비교는 `compareTo`/
  `isEqualByComparingTo`(scale 민감 `equals` 금지), 비율(할인율 등 무차원 계수)은 `double` 유지
  — [ADR-0006](./docs/adr/0006-money-as-bigdecimal.md).
- native 쿼리는 **반드시 파라미터 바인딩**(문자열 결합 금지). admin 보호 엔드포인트는 `X-Admin-Token` 검사.
- 버전 카탈로그 미사용 — Spring 의존성은 BOM이 관리하므로 버전 문자열 없이 선언.

## ⚠️ 함정 — 무심코 "고치지" 말 것

아래는 **아직 남아있는** 알려진 결함이다. 새 코드에서 모방하지 말고, 손대는 김에 (동작이 바뀌는
수정이면 현재 동작을 고정하는 characterization 테스트를 먼저 쓴 뒤) 개선한다.

- `DateUtils`: static `SimpleDateFormat`(thread-unsafe, R3) · `parse()` 가 `ParseException` 을 삼키고
  `null` 반환(C4, 호출부 NPE 위험). `now()`=UTC 와 `localToday()`=서버로컬을 **혼용 금지**(달력 날짜엔
  `localToday()`, UTC 주문 시각 집계엔 UTC 기준).
- refund/reserve 의 check-then-act **TOCTOU 경합**(락/유니크 제약 없음 — 동시 요청 2건이 둘 다 통과
  가능; 단일 H2라 당장 영향은 작음).
- batch `findAll()` 후 Java 필터(C2, 전체 스캔) · `ValidationUtils` 정규식 과허용·한국 전용(CU3) ·
  엔티티가 FK 없이 `Long` id 만 보유(R7).

> 이미 **고쳐진** 결함의 이력(B1~B7·BT1·E1·A1·CU1·R1·R2·R4·R5·R6·R8·C1·CU2·BigDecimal 전환·설정 외부화)과 남은
> 과제의 우선순위·상태는 [`docs/known-issues.md`](./docs/known-issues.md) 가 단일 출처다 — CLAUDE.md
> 에 중복하지 않는다. 결정 배경은 [`docs/adr/`](./docs/adr/), 변경 이력은 git 을 본다.

## 남은 대형 과제

- **공유 H2 DB 분리/전환** ([ADR-0002](./docs/adr/0002-shared-h2-file-database.md)) — ecommerce↔batch 공유 구조의 결합·동시성 한계. 설정 외부화([ADR-0007](./docs/adr/0007-config-via-environment-variables.md))로 ecommerce·batch 가 같은 `SHOP_DB_*` 를 읽게 해 **분리 지점이 한 곳에 드러나 있다**.
  - **1단계 읽기 경계 명시화 ✅** ([ADR-0008](./docs/adr/0008-batch-read-only-shared-db-consumer.md)) — batch 리포지토리를 `ReadOnlyRepository` 로 좁혀 공유 DB 쓰기를 타입 차원에서 막고, 커넥션 `hikari.read-only` 선언, 공유 read 계약을 `SharedSchemaContractTest` 로 고정. 물리 공유 구조는 그대로.
  - **2단계 물리 분리/외부 DBMS 전환은 미착수** — 인프라 도입으로 "로컬 설정 0개" 정체성이 바뀌므로 ADR-0002 재검토 트리거(다중 인스턴스·데이터량 증가)로 다룬다.

상세·우선순위는 [`docs/known-issues.md`](./docs/known-issues.md) "권장 처리 순서".
