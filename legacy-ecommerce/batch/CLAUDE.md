# batch

> legacy-shop 의 **배치 모듈**. 웹 서버가 아니라, 기동되면 잡 4개를 순서대로 **1회 실행하고 종료**하는
> `CommandLineRunner` 앱이다(포트 없음). ecommerce 의 H2 파일 DB(`~/legacyshopdb`)를 **공유해서 읽는다**.
> 전체 시스템 맥락·기술부채 백로그는 모노레포 문서 [`../docs/`](../docs/) 를 본다.

## 정체성

- Java 21 / Spring Boot 3.5.15 / Gradle. 의존성: `spring-boot-starter`, `spring-boot-starter-data-jpa`,
  `h2`(runtimeOnly) + `core-framework`, `common-util`. **web 의존 없음**.
- `web-application-type: none` — REST 엔드포인트가 없다. `BatchRunner`(`CommandLineRunner`)가 진입점.
- **자체 스키마가 없다.** ecommerce 가 만든 `legacyshopdb` 를 `ddl-auto:none` 으로 **공유**해 읽는다.
  엔티티는 ecommerce 테이블(`orders`/`inventory`/`cart`)을 일부 컬럼만 매핑한 **읽기 전용 프로젝션**(`*Row`).

## 빌드 / 실행 / 테스트

프로젝트 루트(`legacy-ecommerce/`)에서 실행한다. **Windows PowerShell**은 `.\gradlew.bat`,
Bash 도구(POSIX 셸)는 `./gradlew`.

```powershell
.\gradlew.bat :batch:build      # 빌드
.\gradlew.bat :batch:bootRun    # 잡 1회 실행 후 종료
.\gradlew.bat :batch:test       # 테스트 — SettlementJobTest·DailySalesAggregationJobTest (BT1 취소주문 제외 회귀, 각 2개)
```

> ⚠️ `:batch:bootRun` 전에 **ecommerce(:8081)가 먼저 떠서 `legacyshopdb` 스키마를 만들어야** 한다.
> batch 는 `ddl-auto:none` 이라 스키마를 만들지 않는다. 빈 DB 에서 단독 기동하면 테이블이 없어 실패한다.
> 접속 URL 은 `AUTO_SERVER=TRUE` 라 ecommerce 가 떠 있는 동안에도 같은 파일에 붙을 수 있다.

## 구조 한눈에

```
src/main/java/com/legacy/shop/batch/
├── BatchApplication.java        @SpringBootApplication(scanBasePackages="com.legacy.shop")
├── BatchRunner.java             CommandLineRunner — 잡 4개를 순서대로 1회 실행
├── job/                         배치 잡 (@Component, 생성자 주입)
│   ├── SettlementJob            정산: 주문 매출 합계 (BT1 ✅ CANCELLED 제외)
│   ├── DailySalesAggregationJob 일일 집계: 오늘 주문 건수/매출 (BT1 ✅ CANCELLED 제외, DailySales record 반환)
│   ├── InventoryReconciliationJob 재고 대사: 음수 재고 점검
│   └── AbandonedCartCleanupJob  방치 장바구니 리포트 (⚠ 삭제 안 함, 건수만)
├── domain/                      읽기 전용 프로젝션 엔티티 (ecommerce 테이블 매핑)
│   ├── OrderRow → orders        / InventoryRow → inventory / CartRow → cart
│   └── OrderStatus              ⚠ ecommerce 의 enum 을 복제 (공유DB라 import 불가)
└── repository/                  JpaRepository (OrderRow/InventoryRow/CartRow)
```

| 순서 | 잡 | 동작 | 읽는 테이블 |
|------|-----|------|-----------|
| 1 | `SettlementJob.settle()` | 주문 `totalAmount` 합산(CANCELLED 제외 ✅BT1) → 총 매출 출력 | `orders` |
| 2 | `DailySalesAggregationJob.aggregate()` | 오늘(`LocalDate.now()`) 주문 건수·매출 집계(CANCELLED 제외 ✅BT1) → `DailySales` 반환 | `orders` |
| 3 | `InventoryReconciliationJob.reconcile()` | `quantity < 0` 인 재고 행 탐지·카운트 | `inventory` |
| 4 | `AbandonedCartCleanupJob.report()` | 생성 30일 경과 장바구니 **건수만** 리포트 | `cart` |

실행 순서는 `BatchRunner.run()`(`BatchRunner.java:31-39`)에 하드코딩되어 있다.

## 이 모듈에서 일할 때 주의점

- **잡은 읽기 전용으로 유지한다.** 현재 모든 잡이 `findAll()` 로 읽어서 Java 에서 집계/출력만 한다(쓰기·삭제 없음).
  공유 DB 의 소유자는 ecommerce 이므로, batch 에서 쓰기를 추가하려면 먼저 스키마 소유권·동시성(ADR-0002)을 검토한다.
- **엔티티는 `*Row` 프로젝션 패턴**이다. 공통 컨벤션과 달리 `BaseTimeEntity` 미상속, `@GeneratedValue` 없음(`@Id` 만),
  **getter 만**(setter 없음), 필요한 컬럼만 매핑한다. 새 읽기 모델도 이 패턴을 따른다.
- ⚠️ 아래는 known-issues 에 등록된 문제다. **새 코드에서 모방하지 말고**, 손대는 김에 개선한다.
  - **B7 — 집계 타임존 불일치**: 주문 시각은 UTC 로 저장되는데 `DailySalesAggregationJob` 은
    `LocalDate.now()`(서버 로컬)로 비교 → 자정 부근 누락/중복. [`../docs/known-issues.md`](../docs/known-issues.md) B7.
  - **C1 — `System.out.println`**: `BatchRunner` 와 모든 잡이 결과를 표준출력으로 찍는다. SLF4J 로 교체 대상.
  - **C2 — 전체 스캔 후 Java 필터**: `findAll()` 후 Java 루프로 거른다. 리포지토리 쿼리로 DB 에서 거르도록.
  - **취소주문 매출 포함 ✅ 수정됨(2026-06-16, BT1)**: (이전) `SettlementJob`·`DailySalesAggregationJob` 이 `status` 를
    매핑만 하고 필터에 안 써 `CANCELLED` 주문도 합산 → **두 잡 모두 `status == CANCELLED` 행 제외**. batch 모듈 첫 테스트
    인프라(`spring-boot-starter-test`) + `SettlementJobTest`·`DailySalesAggregationJobTest`(`ReflectionTestUtils` 로
    read-only `OrderRow` 픽스처 생성). `aggregate()` 는 테스트 관측용으로 `DailySales(count, revenue)` record 반환(여전히 읽기 전용).
    모노레포 [`../docs/known-issues.md`](../docs/known-issues.md) **BT1**.
  - **`OrderStatus` enum 복제**: ecommerce 와 별개로 정의 → 드리프트 위험(**BT2**). 모노레포 [`../docs/known-issues.md`](../docs/known-issues.md).
- 금액은 `double` 로 다룬다(공통 컨벤션, [ADR-0003](../docs/adr/0003-money-as-double.md)). 합산도 `double` 누적이다.
- ⚠️ `AbandonedCartCleanupJob` 은 이름이 "cleanup" 이지만 **실제 삭제를 하지 않고 건수만 리포트**한다.
  의도된 현재 동작이다 — 무심코 삭제 로직을 추가하지 말 것(공유 DB 쓰기는 별도 검토 필요).

## 더 읽기

모노레포 공통 문서: [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) · [`../docs/known-issues.md`](../docs/known-issues.md)(batch 항목 **BT1 ✅·BT2**·B7·C1·C2) · [`../docs/adr/`](../docs/adr/)
