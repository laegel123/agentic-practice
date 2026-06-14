# batch 모듈 문서

`batch` 모듈에 **한정된** 아키텍처·관례·이슈를 정리한다. 모노레포 전체에 공통으로 적용되는
규칙(계층 규칙, 엔티티/DTO 규칙, 에러 처리, 빌드 등)은 여기서 반복하지 않고
상위 모노레포 문서 [`../../docs/`](../../docs/) 로 링크한다.

## 문서 목록

| 문서 | 내용 |
|------|------|
| [`architecture.md`](./architecture.md) | 잡 파이프라인, 읽기 모델(`*Row`) 프로젝션, 공유 DB, 설정·의존성 |
| [`code-conventions.md`](./code-conventions.md) | batch 고유 관례(잡 패턴, `*Row` 엔티티 규칙)와 공통 컨벤션 대비 차이점 |
| [`known-issues.md`](./known-issues.md) | batch 한정 결함·기술부채 (취소주문 매출 포함, enum 복제 드리프트 등) |

## 함께 보기 (모노레포 공통)

- [`../../docs/architecture.md`](../../docs/architecture.md) — 전체 시스템 구조, 모듈 의존 그래프, DB 토폴로지
- [`../../docs/code-conventions.md`](../../docs/code-conventions.md) — 코드베이스 공통 컨벤션
- [`../../docs/known-issues.md`](../../docs/known-issues.md) — 전체 기술부채 목록(B/R/C 코드 체계)
- [`../../docs/adr/`](../../docs/adr/) — 아키텍처 결정 기록(ADR), 특히 [ADR-0002](../../docs/adr/0002-shared-h2-file-database.md)(공유 DB)
- [`../CLAUDE.md`](../CLAUDE.md) — AI 어시스턴트/개발자용 모듈 빠른 안내
