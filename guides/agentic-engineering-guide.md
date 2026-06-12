# 에이전틱 엔지니어링 가이드

> **에이전틱 엔지니어링** = 코드베이스를 *AI 에이전트가 잘 다룰 수 있게* 만드는 일.
> 컨텍스트(문서/규약/맵)를 제공하고, 구조를 명확히 하고, 의도를 코드 밖에 남긴다.
> 하네스(자동화 도구)와는 별개지만 짝을 이룬다 → [harness-engineering-guide.md](harness-engineering-guide.md)

대상: `legacy-ecommerce/`. 효과 측정: [../evaluation/](../evaluation/) 의 두 점수표.
참고 모범사례(이 워크스페이스 안): `overseas/overseas-cost-app/CLAUDE.md`, `docs/`.

## 핵심 원칙
1. **에이전트는 컨텍스트가 없으면 매번 처음부터 탐색한다.** 탐색을 줄이면 턴·토큰·시간이 준다.
2. **의도는 코드에 안 적혀 있다.** 왜 이렇게 했는지(결정/제약)를 문서로 남긴다.
3. **한 파일/한 함수가 한 가지 일을 하면** 에이전트가 안전하게 바꾼다(회귀 ↓).
4. **이름과 경계가 명확하면** 에이전트가 옳은 위치를 빨리 찾는다.

## 체크리스트 (각 항목 → 움직이는 지표)

### 1) 루트 `CLAUDE.md` 작성 → (정적 #13, 에이전트 턴/토큰 ↓)
- 빌드/실행 명령(아래 "실행" 그대로), 포트, DB 위치(`~/legacyshopdb`).
- 모듈 맵 + 의존성 방향(common-util ← core-framework ← 서비스들).
- 규약: 금액 처리 방식, 응답 포맷(`ApiResponse`), 예외(`BusinessException`/`ErrorCode`).
- "하지 말 것"(예: 결제 호출을 트랜잭션 안에서 늘리지 말기 등 알려진 함정).

### 2) 모듈별 컨텍스트 / 아키텍처 맵 → (정적 #8)
- 각 모듈 폴더에 짧은 `README` 또는 루트 `docs/architecture.md`.
- "주문 한 건"의 호출 흐름도(Controller→Service→Repo, ecommerce→payment HTTP).
- 공유 DB 안티패턴(batch↔ecommerce) 같은 **함정**을 명시.

### 3) ADR(아키텍처 결정 기록) → (정적 #8)
- `docs/adr/0001-h2-shared-db.md` 처럼 "왜 이렇게 됐나"를 1건씩.
- 에이전트가 "왜 이 구조죠?"에 시간 안 쓰게.

### 4) 도메인 용어집 → (이해 과제 C* 성공률 ↑)
- 상품/재고/주문/쿠폰/결제/환불/정산의 정의와 상태 전이(OrderStatus, PaymentStatus).

### 5) 거대 클래스/메서드 분해 → (정적 #9, 회귀 ↓)
- `OrderService.placeOrder`(S1) 를 책임별로 쪼갠다(과제 R2).
- 분해 자체가 에이전트가 부분 수정할 표면을 만든다.

### 6) 일관된 이름·경계 → (툴콜/턴 ↓)
- raw id vs `@ManyToOne` 혼용(S13), 응답 타입 혼용 등 통일.
- 패키지 규약 문서화(`domain/repository/service/web/dto`).

### 7) 의도 주석(꼭 필요한 곳만) → (이해 ↑)
- 트레이드오프/함정 지점에 "왜"를 한 줄. 자명한 코드에 군더더기 금지.

## 실행 (CLAUDE.md 에 그대로 넣을 것)
```
cd legacy-ecommerce
./gradlew build
./gradlew :payment-service:bootRun      # 8082
./gradlew :ecommerce-service:bootRun    # 8081 (첫 기동 시 시드)
./gradlew :admin:bootRun                # 8083
./gradlew :batch:bootRun                # 잡 1회 실행 후 종료
# DB 초기화: ~/legacyshopdb.* , ~/legacypaydb.* 삭제
```

## 측정 루프
1. 적용 전: 이해 과제(C1,C3,C4)와 버그 과제 몇 개를 에이전트에게 시키고 [agent-scorecard](../evaluation/agent-scorecard.md) 기록.
2. 위 체크리스트 적용.
3. 같은 과제 재실행 → 턴/토큰/되물음/성공률 변화를 비교.

> 기대: 이해 과제 성공률 ↑, 첫 유효행동까지의 턴 ↓, "이게 어디 있죠?"류 탐색 ↓.
