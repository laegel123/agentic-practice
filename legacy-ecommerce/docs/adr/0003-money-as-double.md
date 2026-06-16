# ADR-0003: 금액을 `double` 로 표현

- **상태**: 대체됨(Superseded by [ADR-0006](./0006-money-as-bigdecimal.md), 2026-06-16) — 금액을 `BigDecimal` 로 전환
- **날짜**: 2026-06-12 (사후 기록) · 2026-06-16 대체
- **결정자**: 원 개발팀 (복원)

## 맥락 (Context)

상품가/소계/세금/할인/결제·환불 금액을 저장·계산해야 했다. 초기에 가장 단순한 원시 타입으로
빠르게 구현했다.

## 결정 (Decision)

모든 금액을 `double` 로 저장하고 계산한다(엔티티 컬럼, DTO 필드, 서비스 계산 전반).
소수 둘째자리 정리는 `common-util` 의 `MoneyUtils.round()` 한 곳에 모은다.

## 대안 (Alternatives)

- **`BigDecimal`**: 십진 정밀도를 보장하는 금액 표준 타입. 다만 코드가 장황해지고, 도입 시점엔
  과하다고 판단해 미뤘다(현재 시점에선 이 판단이 부채로 남았다).
- **정수(최소 화폐단위, 예: 원 단위 long)**: 정밀도는 안전하나 비율 할인·세율 계산에서 변환이 번거롭다.

## 결과 (Consequences)

- (+) 코드가 짧고 산술이 직관적이다.
- (−) **부동소수 오차**: 세금/할인/합계 누적에서 미세 오차가 발생할 수 있다.
- (~) ✅ **B3 해결됨(2026-06-16)**: `MoneyUtils.round()` 가 `Math.floor`(버림) → `BigDecimal.setScale(2, HALF_UP)`
  반올림으로 교체됐다(`MoneyUtilsTest` 회귀). 다만 값은 여전히 `double` 이라 부동소수 오차의 근본 해결은 아니다.
  [known-issues.md](../known-issues.md) B3.
- (~) ✅ **R6 해결됨(2026-06-16)**: `admin` 의 `AdminPriceCalculator` 가 계산식을 복붙(자체 `Math.floor`)하던
  것을 공용 `MoneyUtils.taxOf`/`round`(HALF_UP) 위임으로 교체했다 → 정밀도 정책이 한 곳(`MoneyUtils`)으로 모이고
  B3 이후 ±0.01 어긋나던 분기도 사라졌다(`AdminPriceCalculatorTest` 회귀). [known-issues.md](../known-issues.md) R6.
- **재검토 트리거**: 정산/회계 정합성 요구가 커지는 시점. 근본 해결은 금액 타입을 `BigDecimal`
  로 전환하는 것이며, 이는 엔티티·DTO·DB 컬럼·직렬화까지 파급되는 대형 과제다(완화책인 round 반올림은 ✅ 완료).
- ✅ **대체됨(2026-06-16)**: 금액 타입을 `BigDecimal` 로 전환했다 → [ADR-0006](./0006-money-as-bigdecimal.md).
  이 ADR 의 `double` 결정과 그 부동소수 부채는 더 이상 유효하지 않다(완화책 round 는 BigDecimal scale 2/HALF_UP 로 흡수).
