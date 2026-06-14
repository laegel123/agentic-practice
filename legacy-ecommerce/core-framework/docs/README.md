# core-framework 모듈 문서

`core-framework` 모듈에 **한정된** 아키텍처·관례를 정리한다. 모노레포 전체에 공통으로 적용되는
규칙(계층 규칙, 엔티티/DTO 규칙, 에러 처리 흐름, 빌드 등)은 여기서 반복하지 않고 상위 모노레포 문서
[`../../docs/`](../../docs/) 로 링크한다.

## 문서 목록

| 문서 | 내용 |
|------|------|
| [`architecture.md`](./architecture.md) | 책임, 의존 그래프상 위치, 패키지/계층, 클래스 인벤토리, `ApiResponse`/`ErrorCode` 계약, 상태/스레드 안전성, 빌드 |
| [`code-conventions.md`](./code-conventions.md) | 기반 라이브러리 고유 관례(에러코드 추가·응답 포맷·예외 던지기·감사 엔티티)와 공통 컨벤션 대비 차이점 |

> 이 모듈 한정 결함은 별도 known-issues 문서 대신 위 두 문서에 인라인으로 표기하고, 코드 체계
> (B5/R4/B6 등)는 모노레포 [`../../docs/known-issues.md`](../../docs/known-issues.md) 를 따른다.

## 함께 보기 (모노레포 공통)

- [`../../docs/architecture.md`](../../docs/architecture.md) — 전체 시스템 구조, 모듈 의존 그래프, 서비스 간 통신
- [`../../docs/code-conventions.md`](../../docs/code-conventions.md) — 코드베이스 공통 컨벤션
- [`../../docs/known-issues.md`](../../docs/known-issues.md) — 전체 기술부채 목록(B/R/C 코드 체계)
- [`../../docs/adr/`](../../docs/adr/) — 아키텍처 결정 기록(ADR)
- [`../CLAUDE.md`](../CLAUDE.md) — AI 어시스턴트/개발자용 모듈 빠른 안내
