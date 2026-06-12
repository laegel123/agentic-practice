# 측정 가이드

전/후 비교가 **공정**하려면 측정 방법을 고정해야 한다. 아래 절차를 그대로 쓴다.

## 0. 공통 원칙
- 같은 **과제**, 같은 **모델/설정**, 같은 **시작 상태**(깨끗한 브랜치 + 초기화된 DB)로 before/after 를 맞춘다.
- 에이전트에게는 `legacy-ecommerce/` 만 연다. `evaluation/`(정답지)는 절대 컨텍스트에 넣지 않는다.
- 한 과제 = 한 세션. 끝나면 변경을 버리거나(`git restore`/브랜치 폐기) 다음 과제용으로 분리.

## 1. 정적 지표 (static-scorecard)
```
pwsh evaluation/scripts/measure.ps1
```
- 빌드: `cd legacy-ecommerce; ./gradlew clean build` → 성공/실패.
- 테스트/커버리지: 하네스 적용 후 `./gradlew test` + Jacoco 리포트(`build/reports/jacoco`).
- 정적분석: 적용 후 `./gradlew check` 의 Checkstyle/SpotBugs/PMD 위반 수.
- 문서 루브릭(0~3): 0=없음, 1=형식만, 2=부분 정확, 3=정확+최신. README/모듈문서/아키텍처/CLAUDE.md/ADR 각각.
- 기능 버그 잔존 수: [answer-key.md](answer-key.md) 의 재현 시나리오를 돌려 살아있는 버그 수를 센다.

## 2. 에이전트 효율 지표 (agent-scorecard)
한 과제를 시키는 동안 다음을 기록한다.

- **턴 수**: 사용자 입력 → 에이전트 응답 한 쌍을 1턴. 끼어든 수정/재지시 포함.
- **도구 호출 수**: 에이전트가 실행한 툴콜 개수(파일읽기/편집/배시 등). 대략치 OK.
- **토큰**: Claude Code 에서 `/cost` 입력 또는 세션 하단 사용량 표시를 과제 시작/종료 시점에 읽어 차이를 기록.
- **시간**: 과제 시작~"완료" 선언까지 wall-clock(스톱워치).
- **권한 프롬프트**: 권한 승인 요청이 뜬 횟수. (하네스 적용 후 `.claude/settings.json` allowlist 로 줄어드는 게 핵심 효과)
- **추가 질문**: 에이전트가 진행 위해 사람에게 되물은 횟수.
- **자가검증(O/X)**: 에이전트가 스스로 `./gradlew build`/`test` 등을 돌려 결과를 확인했는가.
- **회귀(O/X·건수)**: 과제 후 `./gradlew build` + 기존 재현 시나리오를 다시 돌려 다른 게 깨졌는지.

## 3. 성공 판정
- **버그 과제(B*)**: ① 정답지의 원인 위치를 맞췄고 ② 올바르게 고쳤고 ③ 회귀 없음. 셋 다면 성공, 일부면 '부분'.
- **이해 과제(C*)**: 정답지의 `file:method` 와 대조해 핵심을 짚었으면 성공.
- **기능 과제(F*)**: 수용 기준 충족 + 빌드 통과.
- **리팩터링(R*)**: 동작 동일(빌드/시나리오 유지) + 목표 구조 달성.
- **하네스/에이전틱(H*)**: 합격선([answer-key.md](answer-key.md) E절) 충족.

## 4. 버그 재현 빠른 참조
서비스 2개 띄운 상태(`:payment-service:bootRun`, `:ecommerce-service:bootRun`)에서:
- 페이지네이션(D7): `GET http://localhost:8081/api/products?page=1&size=5` 가 6번 상품부터 반환.
- 장바구니 합계(D9): 같은 상품 3개 담고 `GET /api/carts/1` 의 `total` 확인.
- 주문 금액(D1/D2): 카트 담고 `POST /api/orders {customerId, couponCode:"SAVE20"}` 의 `totalAmount`.
- 재고 이중차감(D3): 주문 전후 `GET /api/products/1/stock`.
- 쿠폰 만료(D5): `couponCode:"WELCOME"` 로 주문 → 거부.
- 환불 초과(D4): `POST http://localhost:8082/api/payments/refund` 를 결제액 초과로 2회.
- 어드민 권한(D8): `:admin:bootRun` 후 토큰 없이 `POST http://localhost:8083/admin/refunds`.
- 정산(D6): `:batch:bootRun` 콘솔의 `[정산] 총 매출`.
- SQL인젝션(D11): `GET /api/products/search?keyword=' OR 1=1 OR name LIKE '` → 전체 반환 (정상 키워드 'zzz' 는 0건).

자세한 기대값은 [answer-key.md](answer-key.md).
