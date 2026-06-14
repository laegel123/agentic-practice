# batch 코드 컨벤션

> 이 모듈은 모노레포 공통 컨벤션 [`../../docs/code-conventions.md`](../../docs/code-conventions.md) 을
> 기본으로 따른다(패키지 루트 `com.legacy.shop.batch`, 생성자 주입, `private final`, Lombok 없음,
> `@Enumerated(STRING)`, 한국어 주석/영어 식별자, 버전 카탈로그 미사용 등). 아래에는 **batch 에 한정된
> 보충·차이점만** 적는다.

## 구조상의 차이

- **오프라인 배치 모듈**이라 공통 컨벤션의 `web / service / dto / client / config` 계층이 **없다**.
  대신 `job`(배치 로직) + `domain`·`repository`(공유 DB 읽기)만 둔다.
- 진입점은 컨트롤러가 아니라 `BatchRunner`(`CommandLineRunner`)다. 잡 호출 순서가 여기에 하드코딩된다.
- 응답 봉투(`ApiResponse`)·`BusinessException` 을 거의 쓰지 않는다. 결과는 표준출력으로 내보낸다(현재).

## 잡(Job) 작성 패턴 (이 모듈의 핵심 규칙)

각 잡은 `@Component` 이고, 리포지토리를 생성자 주입으로 받아 하나의 공개 메서드를 노출한다.

```java
/**
 * 정산 잡: 전체 주문의 매출 합계를 낸다.
 */
@Component
public class SettlementJob {

    private final OrderRowRepository orderRowRepository;

    public SettlementJob(OrderRowRepository orderRowRepository) {   // 생성자 주입만
        this.orderRowRepository = orderRowRepository;
    }

    public double settle() {                                        // 동사형 단일 진입 메서드
        double revenue = 0;
        for (OrderRow o : orderRowRepository.findAll()) { ... }     // findAll → Java 집계
        System.out.println("[정산] 총 매출 = " + revenue);          // ⚠ 현재 스타일 (C1)
        return revenue;
    }
}
```

- 새 잡도 **`@Component` + 생성자 주입 + 동사형 단일 메서드** 형태를 따른다(`settle`/`aggregate`/`reconcile`/`report`).
- 새 잡을 추가하면 `BatchRunner` 의 생성자에 주입하고 `run()` 에 호출을 **명시적으로** 끼운다
  (자동 발견·리플렉션 없음 — 실행 순서가 코드에 드러나야 한다).
- 클래스 상단에 한 줄 `/** ... */` 로 잡의 역할을 적는 기존 스타일을 유지한다.

## 읽기 모델(`*Row` 엔티티) 규칙 — 공통 엔티티 규칙과 다름

batch 엔티티는 공유 DB 의 기존 테이블을 **읽기만** 하는 프로젝션이다. 공통 컨벤션과 의도적으로 어긋난다.

| 항목 | 공통 컨벤션(ecommerce/payment) | batch `*Row` |
|------|------------------------------|-------------|
| 기반 클래스 | `BaseTimeEntity` 상속 | **상속 안 함** |
| PK | `@GeneratedValue(IDENTITY)` | `@Id` 만(기존 PK 를 읽음, 생성 안 함) |
| 접근자 | getter/setter 수기 작성 | **getter 만**(setter·기본생성자 외 없음) |
| 매핑 범위 | 테이블 전체 컬럼 | **필요한 컬럼만** |
| 네이밍 | `Order`, `Cart` | `OrderRow`, `CartRow`(읽기모델임을 `Row` 접미사로 표시) |

- 새 읽기 모델도 이 패턴을 따른다: `@Entity @Table(name="...")`, 매핑할 컬럼 필드 + getter, **setter 금지**.
- `@Enumerated(EnumType.STRING)` 은 공통과 동일하게 쓴다(`OrderRow.status`).
- ⚠️ enum 타입(`OrderStatus`)을 ecommerce 에서 import 할 수 없어 **복제**한다. 두 정의가 어긋나면 읽기가 깨질 수
  있다(BT2). ecommerce 에서 상태값을 추가하면 batch 의 enum 도 함께 맞춘다 — [known-issues](./known-issues.md).

## 쓰기 금지(현재 동작) 원칙

- 모든 잡이 `findAll()` 로 **읽기만** 한다. batch 는 공유 DB 의 **소유자가 아니다**(ecommerce 가 소유).
- `AbandonedCartCleanupJob` 처럼 이름이 "cleanup" 이어도 **삭제하지 않는다**. 무심코 쓰기/삭제를 추가하지 않는다.
  쓰기가 정말 필요하면 스키마 소유권·동시성([ADR-0002](../../docs/adr/0002-shared-h2-file-database.md))을 먼저 검토한다.

## ⚠️ 모방 금지 안티패턴 (이 모듈에 존재)

손대는 김에 개선하되, **새 코드에서 그대로 답습하지 않는다.** 코드(B7/C1/C2)는 모노레포
[`../../docs/known-issues.md`](../../docs/known-issues.md) 체계를 그대로 쓴다.

- **C1 — `System.out.println`**: `BatchRunner` 와 모든 잡이 결과를 표준출력으로 찍는다. SLF4J 로거로 교체 대상.
- **C2 — 전체 스캔 후 Java 필터**: `findAll()` 로 다 읽고 Java 루프로 거른다(`aggregate`/`reconcile`/`report`).
  데이터가 커지면 비효율 — 리포지토리 쿼리 메서드로 DB 에서 거르도록 바꾼다.
- **B7 — 집계 타임존**: `DailySalesAggregationJob` 의 `LocalDate.now()`(서버 로컬) vs UTC 저장 시각 불일치.
  새 날짜 비교 코드는 기준 타임존을 명시한다.

## 금액 처리

- 금액은 `double` 로 다루고 누적도 `double` 이다(공통 컨벤션, [ADR-0003](../../docs/adr/0003-money-as-double.md)).
- batch 는 금액을 **합산만** 하므로 `MoneyUtils.round`(버림)를 직접 부르지 않지만, `double` 누적의
  부동소수 오차는 동일하게 존재한다. 새 금액 로직이 필요하면 `common-util` 의 `MoneyUtils` 를 재사용한다.

## 주석 / 언어

- 본문 주석·도메인 용어는 한국어, 식별자/타입은 영어(공통 규칙과 동일).
- 잡·엔티티 클래스 상단의 한 줄 `/** ... */` 역할 주석을 유지한다. 과도한 주석은 더하지 않는다.
