# 하네스 엔지니어링 가이드

> **하네스 엔지니어링** = 에이전트가 *안전하고 빠르게* 일하도록 둘러싼 **도구/자동화**를 만드는 일.
> 빌드·테스트·정적분석·CI·훅·권한설정·검증 루프. 에이전트가 스스로 결과를 확인(자가검증)하게 만드는 게 핵심.
> 컨텍스트 쪽은 → [agentic-engineering-guide.md](agentic-engineering-guide.md)

대상: `legacy-ecommerce/`. 효과 측정: [../evaluation/](../evaluation/).
참고 모범사례: `overseas/overseas-cost-app/.github/workflows/`, `phases/`.

## 핵심 원칙
1. **자가검증 가능해야 한다.** 테스트/빌드가 한 방에 돌면 에이전트가 스스로 맞는지 확인한다(회귀 ↓).
2. **빠른 피드백.** 포매터·정적분석이 즉시 위반을 알려주면 왕복이 준다.
3. **권한 마찰 제거.** 자주 쓰는 안전 명령은 미리 허용 → 권한 프롬프트 ↓.
4. **가드레일.** 훅/CI 가 깨진 변경을 막는다.

## 체크리스트 (각 항목 → 움직이는 지표)

### 1) 의존성 버전 중앙관리 → (정적 #6)
- `gradle/libs.versions.toml` 버전 카탈로그로 흩어진 버전(`commons-lang3:3.12.0` 등, S11) 통합.
- 또는 루트에서 `platform`/BOM 으로 일원화.

### 2) 테스트 하네스 → (정적 #2/#3, 자가검증 ↑, 회귀 ↓)
- JUnit5 + 슬라이스 테스트. 결제 호출은 `@MockBean`/stub.
- 주문 플로우 통합 테스트(과제 H1)부터: 금액 계산·재고·쿠폰 케이스.
- 모듈 `build.gradle`:
```groovy
dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
test { useJUnitPlatform() }
```

### 3) 커버리지 게이트 → (정적 #3)
- Jacoco 적용 + 최소선:
```groovy
plugins { id 'jacoco' }
test.finalizedBy jacocoTestReport
```

### 4) 포매터 → (정적 #5, 왕복 ↓)
- Spotless(google-java-format). `./gradlew spotlessApply`/`spotlessCheck`.

### 5) 정적분석 → (정적 #4)
- Checkstyle / SpotBugs / PMD. 기존 코드가 많으니 **baseline** 으로 시작해 신규만 막는 전략.
- SpotBugs 는 D11(SQL인젝션 흔적)·S5(예외 삼킴) 류를 잡아준다.

### 6) CI 파이프라인 → (정적 #7)
- `.github/workflows/ci.yml`: JDK21 셋업 → `./gradlew build`(+check, test). PR에서 동작.
- 모범: `overseas/overseas-cost-app/.github/workflows/`.

### 7) `.claude/settings.json` → (권한 프롬프트 ↓, 가드레일)
- `./gradlew` 류, `curl localhost:*` 같은 안전 명령 allow.
- Stop/PostToolUse 훅으로 "수정 후 자동 빌드" 같은 검증 루프.
```json
{
  "permissions": { "allow": ["Bash(./gradlew *)", "Bash(curl http://localhost:*)"] }
}
```

### 8) 슬래시 커맨드 / 스킬 → (반복작업 ↓)
- `/run-service <name>`, `/new-endpoint`, `/repro-bug <id>` 같은 반복 절차를 커맨드화.

### 9) 검증 루프 / eval 하네스 → (회귀 ↓)
- 버그 재현 스크립트([../evaluation/metrics-guide.md](../evaluation/metrics-guide.md) 4절)를 스모크 테스트로 묶어 "고쳤는지/안 깼는지" 자동 확인.

## 적용 순서 추천
버전카탈로그 → 테스트(H1) → CI(H2) → 포매터/정적분석(H5) → settings.json/훅(H4) → 슬래시커맨드.
각 단계 후 `measure.ps1` 재실행 + 같은 벤치마크 재측정.

## 측정 루프
1. 적용 전: 버그/기능 과제를 시키고 **권한 프롬프트 수**·**자가검증 여부**·**회귀**를 기록.
2. 하네스 적용.
3. 재측정 → 권한 프롬프트 ↓, 자가검증 O, 회귀 ↓ 를 확인.

> 기대: 에이전트가 고친 뒤 `./gradlew test` 를 스스로 돌리고, allowlist 덕에 멈춤 없이 진행.
