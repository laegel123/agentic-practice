# ADR-0008: batch 를 공유 shop DB 의 읽기 전용 소비자로 명시한다

- **상태**: Accepted (ADR-0002 보완)
- **날짜**: 2026-06-16
- **결정자**: 유지보수팀

## 맥락 (Context)

`ecommerce-service` 와 `batch` 는 같은 H2 파일 DB(`~/legacyshopdb`)를 공유한다([ADR-0002]).
ecommerce 가 `ddl-auto: update` 로 스키마를 소유·생성하고, batch 는 `ddl-auto: none` 으로 같은
테이블(`orders`/`cart`/`inventory`)을 **읽기 전용 프로젝션**(`*Row`)으로 읽는다. [ADR-0007] 의 설정
외부화로 두 모듈이 같은 `SHOP_DB_*` 환경변수를 읽게 되어, 이 공유 결합이 **한 곳에 드러나 있다**.

[ADR-0002] 가 남긴 결합 위험은 두 가지다.

1. **스키마 강결합** — ecommerce 가 batch 가 읽는 컬럼을 옮기거나 타입을 바꾸면 batch 가 조용히 깨진다.
2. **소유권 경계 부재** — batch 의 리포지토리가 `JpaRepository` 라 `save`/`delete`/`flush` 가 API 표면에
   노출되어, 소유자도 아닌 batch 가 공유 DB 에 실수로 쓰는 길이 열려 있다(현재 잡들은 읽기만 하지만
   타입 차원의 방지턱이 없다).

물리 DB 분리(앱별 독립 DB)나 외부 DBMS(PostgreSQL) 전환은 인프라 도입과 "인프라 없이 즉시 동작 ·
로컬은 설정 0개로 기동"([ADR-0002]·[ADR-0007])이라는 이 코드베이스의 정체성을 바꾼다. 그 큰 전환에
앞서, **물리 공유는 유지한 채 결합의 위험을 통제**하는 작은 증분이 필요했다.

## 결정 (Decision)

물리 H2 공유는 유지하되, batch 의 **읽기 경계를 명시하고 강제**한다.

- **타입 차원의 쓰기 차단**: batch 리포지토리는 `JpaRepository` 대신 신규 `ReadOnlyRepository`
  (marker `Repository` + 읽기 메서드만)를 상속한다. `save`/`saveAll`/`delete`/`deleteAll`/`flush`
  가 API 표면에서 사라져, batch 가 공유 DB 에 쓰려면 **의도적으로** 메서드를 추가해야 한다.
- **커넥션 read-only 선언**: batch `application.yml` 에 `spring.datasource.hikari.read-only: true` 를
  둬 "batch 는 읽기 소비자"라는 의도를 드러낸다(쓰기 차단의 1차 보장은 위 타입 제거).
- **공유 read 계약의 문서화·고정**: batch 가 의존하는 컬럼(부분집합)을 `test/resources/contract/
  shared-schema.sql` 에 계약으로 적고, `SharedSchemaContractTest` 가 그 스키마에 대해 `*Row` 읽기를
  검증한다. ecommerce 가 이 컬럼을 옮기면 read 가 깨져 드리프트가 회귀로 드러난다.

## 대안 (Alternatives)

- **앱별 완전 독립 DB + 동기화/이벤트**: 결합은 사라지나 인프라·코드 복잡도가 크게 는다.
  [ADR-0002] 재검토 트리거에 남긴다.
- **외부 DBMS(PostgreSQL) 전환 + batch 읽기 전용 grant 계정**: 운영 적합성·동시성은 오르지만 Docker/
  인프라 도입으로 "로컬 설정 0개" 정체성이 깨진다. 데이터량·다중 인스턴스 운영 시점의 재검토로 미룬다.
- **batch 를 ecommerce HTTP API 조회로 전환**: DB 직접 접근을 없애 결합을 완전 제거하나, 대량 집계에
  비효율적이라 [ADR-0002] 가 이미 기각했다.

→ 위 셋은 모두 **물리 구조 전환**이라 비용이 크다. 이 ADR 은 그 전에 **결합 위험만 즉시 낮추는**
경계 명시화를 택했다(가장 작은 증분, 동작 보존).

## 결과 (Consequences)

- (+) batch 가 공유 DB 에 실수로 쓰는 길이 **타입 차원에서** 막힌다(`ReadOnlyRepository`).
- (+) 공유 read 계약이 코드·테스트로 드러나, ecommerce 스키마 드리프트가 **회귀로 잡힌다**.
- (+) 물리 분리 없이 결합 위험을 낮춰 **인프라 0 · 로컬 설정 0개 정체성을 유지**한다(정상 동작 보존 —
  잡들은 종전과 똑같이 읽고 집계한다).
- (−) **물리 공유는 그대로다** — 기동 순서 의존(ecommerce 가 먼저 스키마 생성)과 파일 DB 동시성 한계는
  남는다. 진짜 분리는 [ADR-0002] 재검토 트리거에 남는다.
- (−) 계약 스키마(`shared-schema.sql`)는 ecommerce 실제 스키마의 **수기 복제 표현**이라, ecommerce 가
  컬럼을 바꾸면 이 파일도 함께 갱신해야 한다(어긋나면 `SharedSchemaContractTest` 가 알린다).
- **회귀 방지**:
  - `ReadOnlyBoundaryTest` — 리포지토리가 쓰기 메서드를 노출하지 않음을 박제(리플렉션).
  - `SharedSchemaContractTest` — 계약 스키마에 대해 `*Row` 가 의존 컬럼을 읽어옴을 박제(@DataJpaTest).
  - `ConfigExternalizationTest` — 커넥션 `read-only` 선언을 박제.

## 관계 (Relationship)

이 ADR 은 [ADR-0002] 를 **보완(refine)**한다. 공유 H2 결정 자체를 **대체하지 않는다** — 공유는
유지하되 그 결합을 통제하는 단계다. 물리 구조 전환이 결정되면 그때 [ADR-0002] 를 대체하는 새 ADR 을
연다.

[ADR-0002]: ./0002-shared-h2-file-database.md
[ADR-0007]: ./0007-config-via-environment-variables.md
