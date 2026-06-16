# DB 마이그레이션 스크립트 (수동 적용)

> 이 프로젝트에는 **Flyway/Liquibase 가 없다.** 스키마는 `ddl-auto: update`(운영 파일 DB)와
> `create-drop`(테스트 인메모리)로 Hibernate 가 만든다. 따라서 이 디렉터리의 `.sql` 은 **자동 실행되지
> 않으며**, 타입 변경처럼 `ddl-auto: update` 가 수행하지 못하는 마이그레이션을 **사람이 1회 실행**하는
> 산출물이다.

## 0006 — 금액 `double` → `DECIMAL(19,2)` ([ADR-0006](../../docs/adr/0006-money-as-bigdecimal.md))

금액 타입을 `double` → `BigDecimal` 로 전환했다. 엔티티 컬럼에 `@Column(precision = 19, scale = 2)` 를
달았지만 **`ddl-auto: update` 는 기존 컬럼의 타입을 바꾸지 않는다** — 기존 파일 DB 의 물리 컬럼은 그대로
`DOUBLE` 로 남아 정밀도 이득이 실현되지 않는다(ADR-0006 §3). 아래 스크립트가 그 물리 컬럼을 실제로
`DECIMAL(19,2)` 로 옮긴다.

- [`0006-money-double-to-decimal-ecommerce.sql`](./0006-money-double-to-decimal-ecommerce.sql)
  — `~/legacyshopdb` (ecommerce-service 소유, **batch 가 공유** — [ADR-0002](../../docs/adr/0002-shared-h2-file-database.md))
- [`0006-money-double-to-decimal-payment.sql`](./0006-money-double-to-decimal-payment.sql)
  — `~/legacypaydb` (payment-service)

### 적용 방법 (H2)

1. **두 DB 를 보는 모든 프로세스를 멈춘다** (ecommerce-service · batch · payment-service).
   `AUTO_SERVER=TRUE` 라도 DDL 변경은 단독 접속에서 하는 게 안전하다.
2. 각 스크립트를 해당 DB 에 1회 실행한다. 예:
   ```sh
   java -cp h2-*.jar org.h2.tools.RunScript \
     -url "jdbc:h2:file:~/legacyshopdb" -user sa \
     -script db/migration/0006-money-double-to-decimal-ecommerce.sql
   java -cp h2-*.jar org.h2.tools.RunScript \
     -url "jdbc:h2:file:~/legacypaydb" -user sa \
     -script db/migration/0006-money-double-to-decimal-payment.sql
   ```
   (H2 Console 의 *Run SQL script* 로 붙여넣어도 된다.)
3. 서비스를 재기동한다.

`DOUBLE` → `DECIMAL(19,2)` 변환 시 H2 가 기존 값을 scale 2 로 반올림하므로,
`100.0999999…` 같은 부동소수 노이즈가 이때 정리된다.

### 멱등성

각 문은 `... SET DATA TYPE DECIMAL(19,2)` 로, **이미 `DECIMAL(19,2)` 인 컬럼에 재실행해도 no-op** 이다.
스크립트를 두 번 돌려도 안전하다. (단, 대상 테이블/컬럼이 아직 없는 **완전 신규** DB 에는 실행할 필요가
없다 — Hibernate 가 처음부터 `DECIMAL(19,2)` 로 만든다.)

### 데이터가 없는 로컬/테스트 환경

보존할 데이터가 없다면 마이그레이션 대신 **파일 DB 폐기 후 재생성**이 가장 단순하다:
`~/legacyshopdb*.db` · `~/legacypaydb*.db` 삭제 → 서비스 재기동(엔티티 기준 `DECIMAL(19,2)` 로 재생성).
테스트 프로파일은 인메모리 `create-drop` 이라 매 실행 자동 재생성되어 이 스크립트가 불필요하다.
