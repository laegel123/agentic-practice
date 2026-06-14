# batch 아키텍처

> 이 문서는 `batch` 모듈 **내부** 구조에 집중한다. 공유 DB 토폴로지·모듈 의존 그래프·전체 그림은
> 모노레포 [`../../docs/architecture.md`](../../docs/architecture.md) (특히 "데이터베이스 토폴로지" 절)을 본다.

## 책임

정산·일일매출집계·재고대사·방치장바구니 리포트를 수행하는 **오프라인 배치**. 웹 서버가 아니며,
기동되면 잡 4개를 순서대로 **1회 실행하고 프로세스가 종료**된다(`web-application-type: none`).
자체 도메인 모델·스키마를 소유하지 않고, ecommerce 가 만든 `legacyshopdb` 를 **읽기 전용으로 공유**한다.

## 패키지 / 계층

공통 컨벤션의 `web / service / dto / client / config` 계층이 **없다**. 대신 배치 고유의 `job` 계층과,
공유 DB 를 읽기 위한 `domain`(프로젝션)·`repository` 만 둔다. 흐름은 단방향이다.

```
BatchRunner (CommandLineRunner)
  → job (Settlement / DailySalesAggregation / InventoryReconciliation / AbandonedCartCleanup)
      → repository (JpaRepository.findAll)
          → domain (*Row 프로젝션 엔티티)
              → (JDBC) ~/legacyshopdb  ← ecommerce 가 소유·생성한 스키마
```

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| (루트) | `BatchApplication` | `@SpringBootApplication(scanBasePackages = "com.legacy.shop")` |
| (루트) | `BatchRunner` | `CommandLineRunner`. 잡 4개를 정해진 순서로 1회 호출 |
| `job` | `SettlementJob` | 전체 주문 매출 합계 |
| `job` | `DailySalesAggregationJob` | 오늘 주문 건수·매출 집계 |
| `job` | `InventoryReconciliationJob` | 음수 재고 탐지 |
| `job` | `AbandonedCartCleanupJob` | 30일 경과 장바구니 건수 리포트 (삭제 없음) |
| `domain` | `OrderRow` / `InventoryRow` / `CartRow` | `orders` / `inventory` / `cart` 읽기 전용 매핑 |
| `domain` | `OrderStatus` | ecommerce enum 복제(공유DB라 import 불가) |
| `repository` | `OrderRowRepository` / `InventoryRowRepository` / `CartRowRepository` | `JpaRepository<*Row, Long>` |

> `scanBasePackages = "com.legacy.shop"` 로 그룹 전체를 스캔해야 `core-framework`·`common-util` 의
> 공통 빈이 함께 잡힌다(다른 실행 앱과 동일한 규칙). 다만 batch 는 `GlobalExceptionHandler` 같은
> 웹 전용 빈을 실제로 쓰지는 않는다(웹 컨텍스트가 아니므로).

## 잡 파이프라인

`BatchRunner.run()`(`BatchRunner.java:31-39`)이 잡을 **순서대로 동기 호출**한다. 잡 간 트랜잭션 경계나
재시도·체크포인트가 없다(스프링 배치 프레임워크 미사용 — 평범한 `@Component` 들의 순차 호출).

```
===== 배치 시작 =====
1. settlementJob.settle()                    [정산] 총 매출 = ...
2. dailySalesAggregationJob.aggregate()      [집계] 오늘 주문수=..., 매출=...
3. inventoryReconciliationJob.reconcile()    [재고대사] 음수 재고 ... / 건수 = ...
4. abandonedCartCleanupJob.report()          [장바구니정리] 정리 대상(30일 경과) = ...건
===== 배치 종료 =====
```

모든 출력은 `System.out.println` 이다(C1 — 로깅 부채).

### 1. SettlementJob — 정산 (`SettlementJob.java:19-26`)

`orderRowRepository.findAll()` 을 돌며 `totalAmount` 를 `double` 로 누적해 총 매출을 출력한다.
**주문 상태를 구분하지 않는다** — `CANCELLED` 주문도 매출에 포함된다([known-issues](./known-issues.md) BT1).

### 2. DailySalesAggregationJob — 일일 집계 (`DailySalesAggregationJob.java:21-32`)

`today = LocalDate.now()` 와 `o.getOrderedAt().toLocalDate()` 가 같은 주문만 카운트/합산한다.
- ⚠️ **타임존 불일치(B7)**: 주문 시각은 UTC 로 저장되는데 `LocalDate.now()` 는 **서버 로컬** 기준이라
  자정 부근에서 날짜 경계가 어긋난다 → 집계 누락/중복. [모노레포 known-issues B7](../../docs/known-issues.md).
- 상태 필터가 없어 여기서도 `CANCELLED` 주문이 "오늘 매출" 에 포함된다.

### 3. InventoryReconciliationJob — 재고 대사 (`InventoryReconciliationJob.java:19-29`)

`inventoryRowRepository.findAll()` 중 `quantity < 0` 인 행을 찾아 `productId`/`qty` 를 출력하고 건수를 센다.
"대사(reconciliation)" 라는 이름과 달리 외부 기준과 대조하지 않고 **음수 재고만 스캔**한다.
음수 재고 자체가 ecommerce 의 **재고 이중 차감(B1)** 의 하류 증상이다 — [모노레포 known-issues B1](../../docs/known-issues.md).

### 4. AbandonedCartCleanupJob — 방치 장바구니 (`AbandonedCartCleanupJob.java:22-31`)

`cutoff = LocalDateTime.now().minusDays(30)` 보다 `createdAt` 이 이른 장바구니 **건수만** 리포트한다.
이름은 "cleanup" 이지만 **삭제하지 않는다**(코드 주석에 명시). 공유 DB 에 대한 쓰기를 의도적으로 피한 것으로 보인다.

## 읽기 모델 (`*Row` 프로젝션)

batch 는 ecommerce 의 엔티티 클래스를 **공유하지 않고**(별도 모듈·공유DB), 필요한 테이블을 자체 엔티티로
다시 매핑한다. 공통 엔티티 규칙과 의도적으로 다르다.

| 엔티티 | 테이블 | 매핑 컬럼 | 비고 |
|--------|--------|----------|------|
| `OrderRow` | `orders` | `id`, `customerId`, `totalAmount`(double), `status`(enum STRING), `orderedAt` | 일부 컬럼만 |
| `InventoryRow` | `inventory` | `id`, `productId`, `quantity`(int) | |
| `CartRow` | `cart` | `id`, `customerId`, `createdAt` | |

특징(전부 공통 컨벤션과 차이 → [code-conventions](./code-conventions.md)):
- `BaseTimeEntity` **미상속**, `@GeneratedValue` **없음**(`@Id` 만 — 기존 PK 를 읽기만 함).
- **getter 만** 있고 setter·생성자 없음(읽기 전용 의도). 쓰기 경로가 존재하지 않는다.
- `OrderStatus` 는 ecommerce 의 동일 enum 을 **복제**한 것이라 두 정의가 어긋날 수 있다(BT2).

## 설정 (`src/main/resources/application.yml`)

```yaml
spring:
  application: { name: batch }
  main:
    web-application-type: none        # 웹 서버 아님 — CommandLineRunner 로 1회 실행
  datasource:
    url: jdbc:h2:file:~/legacyshopdb;AUTO_SERVER=TRUE   # ecommerce 와 '같은' 파일 DB (공유)
    username: sa
    password:                          # 비밀번호 없음
    driver-class-name: org.h2.Driver
  jpa:
    hibernate: { ddl-auto: none }      # 스키마는 ecommerce 가 만든다 — batch 는 만들지 않음
    show-sql: false
```

- DB 경로가 하드코딩되어 있다(모노레포 R5, 설정 외부화 대상).
- `ddl-auto: none` 이므로 **ecommerce 가 먼저 스키마를 만든 뒤에야** batch 가 동작한다. 빈 DB 단독 기동은 실패.
- `AUTO_SERVER=TRUE` 라 ecommerce 가 떠 있는 동안에도 같은 파일에 동시 접속할 수 있다.

## 의존성

- **빌드 의존**(`batch/build.gradle`): `core-framework`, `common-util`, `spring-boot-starter`,
  `spring-boot-starter-data-jpa`, `h2`(runtimeOnly). **web 의존 없음**.
- **런타임 의존**: `legacyshopdb` 스키마(= ecommerce 가 생성). 권장 기동 순서는 **ecommerce → batch**
  ([`../../docs/architecture.md`](../../docs/architecture.md) "실행 방법").
- `core-framework`·`common-util` 의 공통 기반에 의존하지만, batch 는 응답 봉투(`ApiResponse`)나
  예외 처리(`BusinessException`)를 거의 쓰지 않는다 — 웹 요청-응답이 아니기 때문이다.
