# admin 모듈 문서

`admin` 모듈에 **한정된** 아키텍처·관례·이슈를 정리한다. 모노레포 전체에 공통으로 적용되는
규칙(계층 규칙, 엔티티/DTO 규칙, 에러 처리, 빌드 등)은 여기서 반복하지 않고
상위 모노레포 문서 [`../../docs/`](../../docs/) 로 링크한다.

## 문서 목록

| 문서 | 내용 |
|------|------|
| [`architecture.md`](./architecture.md) | 게이트웨이 패턴, 엔드포인트, 요청 흐름, 설정·의존성 |
| [`code-conventions.md`](./code-conventions.md) | admin 고유 관례(인증 패턴 등)와 공통 컨벤션 대비 차이점 |
| [`known-issues.md`](./known-issues.md) | admin 한정 결함·기술부채 메모 (무인증 `/admin/refunds` 등) |

## 함께 보기 (모노레포 공통)

- [`../../docs/architecture.md`](../../docs/architecture.md) — 전체 시스템 구조, 모듈 의존 그래프, 서비스 간 통신
- [`../../docs/code-conventions.md`](../../docs/code-conventions.md) — 코드베이스 공통 컨벤션
- [`../../docs/known-issues.md`](../../docs/known-issues.md) — 전체 기술부채 목록(B/R/C 코드 체계)
- [`../../docs/adr/`](../../docs/adr/) — 아키텍처 결정 기록(ADR)
- [`../CLAUDE.md`](../CLAUDE.md) — AI 어시스턴트/개발자용 모듈 빠른 안내
