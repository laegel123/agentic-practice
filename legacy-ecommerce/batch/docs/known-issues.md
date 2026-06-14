# batch 알려진 문제 · 기술 부채

> 이 메모는 `batch` 모듈에 한정된 결함/안티패턴을 모은다. 전체 코드베이스의 목록과 분류 체계는
> 모노레포 [`../../docs/known-issues.md`](../../docs/known-issues.md) 에 있다.
>
> 분류: 🐞 정합성/집계 결함 / ⚠️ 위험·안티패턴 / 🧹 정리. 영향도: 높음/중간/낮음.
> 수정 성격: **동작변경**(behavior-changing) / **동작보존**(behavior-preserving).
> 수정 전에는 **현재 동작을 고정하는 characterization 테스트**를 먼저 작성한다.

## batch 신규 발견 (모노레포 목록에 아직 없음)

| # | 위치 | 증상 | 영향 | 수정 성격 |
|---|------|------|------|----------|
| BT1 | `job/SettlementJob.java:19-26`, `job/DailySalesAggregationJob.java:21-32` | **취소 주문이 매출에 포함.** `OrderRow.status`(`CREATED`/`PAID`/`CANCELLED`)를 매핑만 하고 **필터에 전혀 쓰지 않는다.** 정산·일일집계 모두 `CANCELLED` 주문의 `totalAmount` 를 매출에 합산한다. 매출이 과대 계상된다. | 높음 | 동작변경(상태 필터 추가) |
| BT2 | `domain/OrderStatus.java` | **`OrderStatus` enum 복제 → 드리프트 위험.** 공유 DB 라 ecommerce 의 enum 을 import 하지 못해 같은 enum 을 또 정의했다. ecommerce 가 새 상태값을 추가하면 batch 는 모르며, `@Enumerated(STRING)` 으로 **알 수 없는 값을 읽으면 Hibernate 가 예외**를 던진다. | 중간 | 동작보존(동기화·공유) |
| BT3 | `job/AbandonedCartCleanupJob.java:22-31` | **이름 vs 동작 불일치.** "Cleanup" 인데 실제 삭제 없이 건수만 리포트한다(주석에 명시). 의도된 현재 동작이나, 이름만 보고 삭제를 기대하면 오해 소지. 정리 로직을 추가하려면 공유 DB 쓰기 검토 필요. | 낮음 | (의도 확인 후 결정) |

> BT1 은 매출 숫자가 직접 틀어지므로 우선순위가 높다. 수정 시 `status == PAID` 등으로 한정하되,
> "어떤 상태를 매출로 볼지"는 도메인 정의가 필요하다(현재는 **전 상태 포함**이 실제 동작). 모노레포
> `../../docs/known-issues.md` 에 정식 편입할 후보이며, 현재는 이 모듈 문서에만 기록한다.
> BT2 는 ecommerce `OrderStatus` 와 batch `OrderStatus` 를 항상 같이 변경하는 것으로 우선 완화한다.

## 모노레포 목록 중 batch 해당 항목 (참조)

코드 체계는 [`../../docs/known-issues.md`](../../docs/known-issues.md) 와 동일하다. 상세·배경은 링크 참조.

| # | 위치 | 내용 | 수정 성격 |
|---|------|------|----------|
| B7 | `job/DailySalesAggregationJob.java:22,26` | 주문 시각은 **UTC** 로 저장되는데 집계는 `LocalDate.now()`=**서버 로컬**로 비교 → 자정 부근 날짜 경계에서 집계 누락/중복. `DateUtils` 의 `now()`/`localToday()` 혼용과 같은 뿌리. | 동작변경 |
| C1 | `BatchRunner.java`, `job/*` | 결과 출력이 전부 `System.out.println`. SLF4J 로거로 교체. | 동작보존 |
| C2 | `job/*` | `findAll()` 후 Java 루프로 필터/집계(전체 스캔). 리포지토리 쿼리로 DB 에서 거르도록. | 동작보존 |
| R5 | `application.yml` | DB 경로(`~/legacyshopdb`)가 하드코딩. 운영 분리·외부화 대상. | 동작보존(외부화) |
| R7 | `domain/*Row` | `customerId`/`productId` 등 FK 연관 없이 `Long` id 만 보유(참조 무결성 없음). 단 batch 는 읽기 전용이라 영향이 작다. | 동작변경(스키마 영향) |

## 연관 (모듈 외부지만 batch 동작·결과에 영향)

- **재고 이중 차감(B1)** — ecommerce `InventoryService.reserve()`==`confirm()` 로 주문당 재고가 2배 빠진다.
  `InventoryReconciliationJob` 이 탐지하는 **음수 재고**는 사실상 이 버그의 하류 증상이다.
  배경 [모노레포 known-issues B1](../../docs/known-issues.md).
- **공유 H2 파일 DB(ADR-0002)** — batch 는 ecommerce 스키마에 강하게 결합한다. ecommerce 가 먼저 떠야
  하고, 컬럼/enum 변경이 batch 매핑을 깨뜨릴 수 있다. [ADR-0002](../../docs/adr/0002-shared-h2-file-database.md).
- **금액 `double`(B3, ADR-0003)** — batch 의 매출 합산도 `double` 누적이라 부동소수 오차에 노출된다.
  근본 해결은 전사 `BigDecimal` 전환. [ADR-0003](../../docs/adr/0003-money-as-double.md).

## 우선 처리 제안 (batch 한정)

1. **검증 루프** — 잡들의 현재 동작(취소 포함 합산, 타임존 비교, 음수 탐지 카운트)을 고정하는 테스트 먼저.
2. **BT1 / B7** — 매출 숫자에 직접 영향(테스트로 현재 동작 고정 후 단언을 뒤집어 의도를 드러낸다).
3. **C1 / C2** — 로깅 교체, 쿼리로 필터(동작보존).
4. **BT2** — `OrderStatus` 동기화 정책(최소한 ecommerce 변경 시 함께 수정).
