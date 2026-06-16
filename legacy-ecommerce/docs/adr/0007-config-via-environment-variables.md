# ADR-0007: 설정을 환경변수로 외부화한다

- **상태**: Accepted
- **날짜**: 2026-06-16
- **결정자**: 유지보수팀

## 맥락 (Context)

서비스 URL·DB 접속정보(URL·계정·비밀번호)가 각 모듈 `application.yml` 에 **하드코딩**되어 있었다
([known-issues.md](../known-issues.md) **R5**). 그래서 같은 빌드 산출물을 로컬·스테이징·운영에 그대로
띄울 수 없고(환경마다 yml 을 고쳐 다시 빌드해야 함), DB 자격증명이 소스에 박혀 있었다.

이미 `admin.token` 만 먼저 환경변수(`ADMIN_TOKEN`)로 빼고 **fail-closed**(미설정 시 기동 실패)로
바꿔둔 상태였다(A1/R5 후속). 나머지 URL·DB 경로도 같은 방식으로 외부화할 필요가 있었다.

12-Factor 의 "config 는 환경에 둔다" 원칙을 따르되, **로컬 개발은 아무 설정 없이도 즉시 떠야 한다**는
제약(ADR-0002 의 "인프라 없이 즉시 동작")을 깨지 않는 게 force 였다.

## 결정 (Decision)

설정값을 yml 에 `${ENV_VAR:기본값}` placeholder 로 적는다.

- **기본값은 외부화 이전의 하드코딩 리터럴과 byte 단위로 동일**하게 둔다 → 환경변수를 주지 않으면
  종전과 똑같이 동작한다(동작 보존). 로컬 개발은 설정 0개로 그대로 뜬다.
- 운영/스테이징은 환경변수만 주입해 같은 빌드 산출물을 재사용한다.
- **시크릿은 안전한 기본값이 없으므로 fail-closed** 로 둔다(`admin.token` 은 빈 기본값 → 미설정 시
  기동 실패). DB 비밀번호는 기존이 "빈 값"이라 빈 기본값을 유지한다(H2 로컬 한정).
- **공유 결합은 공유 환경변수로 드러낸다**: ecommerce 와 batch 가 같은 shop DB 를 보는 결합(ADR-0002)을
  둘 다 `SHOP_DB_*` 를 읽게 해 명시한다 — 이 키가 곧 분리 지점이다(ADR-0002 진행 시 여기서 갈라진다).

### 환경변수 목록 (단일 출처)

| 환경변수 | 기본값 | 사용 모듈 |
|----------|--------|-----------|
| `SHOP_DB_URL` | `jdbc:h2:file:~/legacyshopdb;AUTO_SERVER=TRUE` | ecommerce(스키마 소유) · batch(공유) |
| `SHOP_DB_USERNAME` | `sa` | ecommerce · batch |
| `SHOP_DB_PASSWORD` | (빈 값) | ecommerce · batch |
| `PAYMENT_DB_URL` | `jdbc:h2:file:~/legacypaydb;AUTO_SERVER=TRUE` | payment |
| `PAYMENT_DB_USERNAME` | `sa` | payment |
| `PAYMENT_DB_PASSWORD` | (빈 값) | payment |
| `ECOMMERCE_BASE_URL` | `http://localhost:8081` | admin |
| `PAYMENT_BASE_URL` | `http://localhost:8082` | ecommerce · admin |
| `ADMIN_TOKEN` | **(없음 — fail-closed)** | admin |

> Spring Boot 의 relaxed binding 으로 `SPRING_DATASOURCE_URL` 같은 빌트인 키도 동작하지만, **공유
> 결합을 한 이름으로 드러내고**(`SHOP_DB_URL`) 시크릿 fail-closed 정책을 명시하려고 의도된 키를 둔다.

## 대안 (Alternatives)

- **Spring 프로파일별 yml**(`application-prod.yml` 등): 환경마다 파일을 만들어야 하고 시크릿이 여전히
  파일에 남는다. 환경변수가 시크릿·CI/CD 주입에 더 맞는다.
- **빌트인 `SPRING_*` 환경변수에만 의존**(yml 무수정): 공유 DB 결합이 이름으로 안 드러나고, 비밀
  기본값 제거(fail-closed) 같은 정책을 표현할 수 없다.
- **외부 설정 서버(Spring Cloud Config / Vault)**: 현재 규모엔 과한 인프라. 재검토 트리거에 남긴다.

## 결과 (Consequences)

- (+) 같은 빌드 산출물을 환경변수만 바꿔 로컬/스테이징/운영에 띄울 수 있다. DB 자격증명이 소스에서
  빠진다. 로컬 개발은 설정 0개로 종전처럼 뜬다(동작 보존).
- (+) `SHOP_DB_*` 공유 키가 ecommerce↔batch 결합을 한 곳에 드러내, ADR-0002(DB 분리)의 작업 지점이
  명확해진다.
- (−) 기본값이 yml 과 일부 코드(`@Value(...:기본)`)에 중복으로 남는다 — 두 기본값은 일치시켜야 한다.
- (−) 환경변수 미설정 시 **로컬 파일 DB 기본값으로 조용히 떨어진다**(시크릿 제외). 운영에서 주입을
  빠뜨리면 의도치 않게 로컬 DB 를 볼 수 있다 — 배포 체크리스트로 관리한다.
- **회귀 방지**: 각 모듈 `ConfigExternalizationTest` 가 application.yml 을 로드해 (1) 기본값이 종전
  리터럴과 같고 (2) 환경변수 주입이 반영되는지 박제한다(컨텍스트·DB 기동 없는 placeholder 해석 테스트).
- **남는 일**: 이 ADR 은 *외부화*만 다룬다. 공유 DB 구조 자체의 분리는 ADR-0002 가 별도로 다룬다.
