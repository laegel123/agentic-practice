# 플레이북: 처음부터 끝까지

레거시에 에이전틱/하네스 엔지니어링을 "입히고" 전/후를 숫자로 비교하는 전체 루프.

## 준비
- 도구: JDK 21, (인터넷 — 최초 의존성 다운로드). 빌드는 `legacy-ecommerce/` 안의 Gradle 래퍼가 알아서 받는다.
- 규칙: 에이전트는 **`legacy-ecommerce/` 만** 연다. `evaluation/`(정답지)·`guides/` 는 컨텍스트에 넣지 않는다.
- 버전관리: 라운드마다 새 브랜치 또는 작업 후 `git restore` 로 시작 상태를 복원.

## 0단계 — 베이스라인이 도는지 확인
```
cd legacy-ecommerce
./gradlew clean build
./gradlew :payment-service:bootRun      # 8082 (별도 터미널)
./gradlew :ecommerce-service:bootRun    # 8081 (별도 터미널, 첫 기동 시 시드)
```
[../evaluation/metrics-guide.md](../evaluation/metrics-guide.md) 4절의 재현 명령으로 버그가 살아있는지 눈으로 확인.

## 1단계 — 베이스라인 측정 (BEFORE)
1. 정적: `pwsh ../evaluation/scripts/measure.ps1` → [static-scorecard](../evaluation/static-scorecard.md) BEFORE 채움.
2. 에이전트: [benchmark-tasks](../evaluation/benchmark-tasks.md) 의 권장 10과제를 **레거시 상태 그대로** 에이전트에게 시키고 [agent-scorecard](../evaluation/agent-scorecard.md) 에 기록.
   - 각 과제는 새 세션·새 브랜치. 끝나면 변경 버림(측정만 하고 머지 안 함).

## 2단계 — 에이전틱 엔지니어링 적용
[agentic-engineering-guide.md](agentic-engineering-guide.md) 체크리스트 수행:
CLAUDE.md, 모듈/아키텍처 문서, ADR, 용어집, (선택)거대 클래스 분해.
→ 같은 이해/버그 과제 재측정. **턴·토큰·이해 성공률** 변화 확인.

## 3단계 — 하네스 엔지니어링 적용
[harness-engineering-guide.md](harness-engineering-guide.md) 체크리스트 수행:
버전카탈로그 → 테스트 → CI → 포매터/정적분석 → settings.json/훅 → 슬래시커맨드.
→ 같은 버그/기능 과제 재측정. **권한 프롬프트·자가검증·회귀** 변화 확인.

## 4단계 — 비교 & 회고
[static-scorecard](../evaluation/static-scorecard.md) / [agent-scorecard](../evaluation/agent-scorecard.md) 의 전/후를 나란히.

| 무엇이 | 베이스라인 | 에이전틱 후 | 하네스 후 |
|--------|-----------|-------------|-----------|
| 이해 과제 성공률 | | | |
| 버그 과제 성공률 | | | |
| 평균 턴/토큰/시간 | | | |
| 권한 프롬프트 | | | |
| 자가검증 비율 | | | |
| 회귀 발생 | | | |
| 빌드/테스트/CI/정적분석 | X/X/X/X | | |

회고 질문:
- 어떤 affordance 가 **가장 큰** 지표 변화를 냈나? (보통 CLAUDE.md + 테스트 + settings.json)
- 에이전트가 여전히 막힌 지점은? → 다음 라운드의 입힐 거리.

## 반복
한 번에 다 하지 말고 **라운드**로 돌린다. 매 라운드: 측정 → 한 가지 입히기 → 재측정.
가장 적은 노력으로 지표를 많이 움직이는 항목을 먼저.

---
### 권장 첫 라운드 (반나절 코스)
0단계 확인 → BEFORE 로 `C1,C3,B1,B3,B7` 측정 → CLAUDE.md + 주문플로우 테스트 1개 + settings.json allowlist 만 적용 → 같은 5과제 AFTER 측정 → 표 채우기.
이 최소 세트만으로도 턴·권한 프롬프트·자가검증에서 차이가 보인다.
