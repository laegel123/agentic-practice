# ADR-0002: H2 파일 DB + 이커머스·배치 공유

- **상태**: Accepted · 재검토 대상
- **날짜**: 2026-06-12 (사후 기록)
- **결정자**: 원 개발팀 (복원)

## 맥락 (Context)

별도 DB 서버를 운영하기 어려운 환경에서 빠르게 동작하는 영속 저장소가 필요했다. 또한 배치
잡(정산/집계/재고대사)이 이커머스가 쌓은 주문·재고 데이터를 그대로 읽어야 했다.

## 결정 (Decision)

- 저장소로 **H2 파일 모드**를 쓴다(`jdbc:h2:file:~/...;AUTO_SERVER=TRUE`).
- 물리 DB는 둘:
  - `~/legacyshopdb` — `ecommerce-service` 가 `ddl-auto: update` 로 스키마를 생성·소유. `batch` 는 같은 파일을 `ddl-auto: none` 으로 **공유**해 읽는다.
  - `~/legacypaydb` — `payment-service` 전용(독립).
- `AUTO_SERVER=TRUE` 로 여러 프로세스의 동시 접속을 허용한다.

## 대안 (Alternatives)

- **앱마다 완전 독립 DB + 동기화/이벤트**: 결합은 줄지만 인프라·코드 복잡도가 크게 는다.
- **PostgreSQL/MySQL 등 외부 DBMS**: 운영 부담. 초기 단계에서 과한 선택으로 판단.
- **배치도 HTTP API로 데이터 조회**: 대량 집계에 비효율적이라 DB 직접 접근을 택했다.

## 결과 (Consequences)

- (+) 인프라 없이 즉시 동작. 로컬 개발이 단순하다.
- (−) **ecommerce ↔ batch 가 스키마 수준에서 강결합**된다. 이커머스가 먼저 떠서 스키마를 만든
  뒤에야 배치가 동작한다(기동 순서 의존). 이커머스의 엔티티 변경이 배치를 조용히 깨뜨릴 수 있다.
- (−) 파일 DB + `AUTO_SERVER` 는 동시성·확장성에 한계가 있고 운영 적합성이 낮다.
- (−) 자격증명/경로가 `application.yml` 에 하드코딩되어 있다([known-issues.md](../known-issues.md) R5).
- **재검토 트리거**: 다중 인스턴스 운영, 데이터량 증가, 또는 ecommerce/batch 스키마 분리가
  필요해지는 시점. 외부 DBMS 전환 + 배치의 데이터 접근 방식 재설계를 함께 검토한다.
