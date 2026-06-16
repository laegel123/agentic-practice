# Architecture Decision Records (ADR)

이 디렉터리는 legacy-shop의 아키텍처 결정을 기록한다. ADR은 "무엇을, 왜, 어떤 대안을
제치고 선택했는가"를 짧게 남기는 문서다.

이 프로젝트는 담당자 퇴사로 결정 기록이 남아 있지 않았다. 따라서 아래 0001~0005는 코드에
**이미 굳어진 결정을 사후적으로(retrospective) 복원**해 기록한 것이다. 일부는 명백한 부채이며,
그 경우 "재검토 대상"으로 상태를 표시한다.

## 작성 규칙

- 파일명: `NNNN-제목-kebab-case.md` (4자리 번호, 순차 증가).
- 새 결정은 `template.md` 를 복사해 작성한다.
- 결정을 바꿀 때는 기존 ADR을 지우지 말고 새 ADR을 만든 뒤, 옛 ADR의 상태를
  `Superseded by ADR-NNNN` 으로 갱신한다.

## 상태값

`Proposed`(제안) · `Accepted`(채택) · `Deprecated`(폐기 예정) · `Superseded`(대체됨).

## 목록

| ADR | 제목 | 상태 |
|-----|------|------|
| [0000](./0000-record-architecture-decisions.md) | ADR로 아키텍처 결정을 기록한다 | Accepted |
| [0001](./0001-multi-module-monolith.md) | 멀티모듈 모놀리스 구조 | Accepted |
| [0002](./0002-shared-h2-file-database.md) | H2 파일 DB + 이커머스·배치 공유 | Accepted · 재검토 대상 |
| [0003](./0003-money-as-double.md) | 금액을 `double` 로 표현 | Superseded by 0006 |
| [0004](./0004-no-gradle-version-catalog.md) | Gradle 버전 카탈로그 미사용 | Accepted |
| [0005](./0005-map-based-inter-service-http.md) | 서비스 간 HTTP를 raw Map으로 통신 | Accepted · 재검토 대상 |
| [0006](./0006-money-as-bigdecimal.md) | 금액을 `BigDecimal` 로 전환 | Accepted (0003 대체) |
| [0007](./0007-config-via-environment-variables.md) | 설정을 환경변수로 외부화 | Accepted |
