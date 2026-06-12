# 정적 저장소 건강도 점수표

저장소 자체의 객관적 속성. `scripts/measure.ps1` + 도구 출력 + 육안으로 채운다.
같은 항목을 **에이전틱/하네스 입히기 전(baseline)** 과 **후(after)** 에 측정해 비교한다.

- Baseline 측정일: 최초 생성 시점 (아래 값은 `measure.ps1` 실측)
- 점수: O(있음/통과) / X(없음/실패) / 부분, 또는 수치

| # | 지표 | 측정 방법 | Baseline | After (목표) |
|---|------|-----------|----------|--------------|
| 1 | 한 방 클린 빌드 성공 | `./gradlew clean build` | **O** (성공) | O 유지 |
| 2 | 테스트 존재/통과 | `:*:test`, test 파일 수 | **X** (0개) | O (핵심 플로우 커버) |
| 3 | 커버리지 % | Jacoco 리포트 | **0%** (도구 없음) | 측정 가능 + 목표선 |
| 4 | 정적분석 구성·통과 | Checkstyle/SpotBugs/PMD | **X** (없음) | O (위반 0 또는 baseline 관리) |
| 5 | 포매터 구성 | Spotless `spotlessCheck` | **X** | O |
| 6 | 의존성 버전 중앙관리 | `gradle/libs.versions.toml` | **X** (모듈마다 직접 박음) | O (카탈로그/platform) |
| 7 | CI 파이프라인 green | `.github/workflows` | **X** | O (빌드+테스트) |
| 8 | 문서 정확/충실 | README·모듈README·아키텍처·CLAUDE.md·ADR | **부실** (루트 README 1개, 포트도 틀림) | O (각 0~3 루브릭) |
| 9 | 최대 파일 LOC / 300↑ 파일수 | `measure.ps1` | **117 / 0개** | 유지·개선 |
| 10 | main 파일수 / 총 LOC / 평균 | `measure.ps1` | **97 / 2597 / 26.8** | (리팩터링 후 변화 추적) |
| 11 | 재현 가능한 셋업 문서화 | 새 클론에서 README대로 | **X** (불명확) | O |
| 12 | 가드레일 | `.claude/settings.json`, pre-commit 훅 | **X** | O |
| 13 | 에이전트 컨텍스트 | `CLAUDE.md` 유무/품질 | **X** | O |

## 악취 근사치 (grep, 방향성 참고용)

| 항목 | Baseline | After |
|------|----------|-------|
| `System.out.println` | 9 | (로깅으로 대체 ↓) |
| `double` 선언(금액 후보) | 95 | (`Money` 도입 후 ↓) |
| `@SuppressWarnings` | 3 | ↓ |
| 가격계산 로직 중복 지점 | 2 (`PricingService`, `AdminPriceCalculator`) | 1 |

## 기능 버그 잔존 수 (answer-key 기준)

| | Baseline | After |
|---|----------|-------|
| 미해결 기능 버그(D1~D11) | **11** | 0 목표 |

> 사용법: 새 측정 시 이 파일을 복사(`static-scorecard-after-<라운드>.md`)해서 After 열을 채우고,
> Baseline 과 나란히 두어 변화를 본다.
