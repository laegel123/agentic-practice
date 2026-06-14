# legacy-shop 문서

원 담당자 퇴사로 방치되었던 프로젝트 문서를 복원·정비한 모음이다. 코드 변경 없이
**문서화 우선** 패스로 작성되었다.

## 목차

- **[architecture.md](./architecture.md)** — 모듈 구조, 의존 그래프, DB 토폴로지, 주문 흐름, 서비스 간 통신, API 표면, 실행 방법.
- **[code-conventions.md](./code-conventions.md)** — 패키지/계층 규칙, 엔티티·DTO·응답·에러 처리 컨벤션, 유틸 재사용 규칙. (⚠️ 표시는 안티패턴)
- **[known-issues.md](./known-issues.md)** — 확인된 정합성 버그·안티패턴·기술부채 백로그와 권장 처리 순서.
- **[adr/](./adr/)** — Architecture Decision Records. 코드에 굳어진 결정을 사후 복원해 기록.

## 읽는 순서 (처음 합류했다면)

1. 루트 [`CLAUDE.md`](../CLAUDE.md) — 한 화면 요약 + 빌드/실행/주의사항.
2. `architecture.md` — 전체 그림.
3. `code-conventions.md` — 코드를 만지기 전에.
4. `known-issues.md` — 무엇이 의도된 것이고 무엇이 부채인지.
5. 필요 시 `adr/` 에서 특정 결정의 배경.

## 현재 상태 메모

- **검증 루프 구축 완료**: `common-util`/`ecommerce-service`/`payment-service`에 characterization
  테스트 28개(JUnit5 + Mockito + AssertJ, `./gradlew test` 전부 green). 인메모리 H2로 실 DB와 격리.
- 이 문서들은 "현재 코드가 실제로 하는 일"을 기술한다. 버그도 **고치지 않고 기록·테스트로 고정**했다
  (수정은 해당 테스트 단언을 같은 커밋에서 뒤집으며 별도 패스에서 진행).
