# common-util 알려진 문제 · 기술 부채

> 이 메모는 `common-util` 모듈에 한정된 결함/안티패턴을 모은다. 전체 코드베이스의 목록과 분류 체계는
> 모노레포 [`../../docs/known-issues.md`](../../docs/known-issues.md) 에 있다.
>
> 분류: 🐞 정합성/보안 결함 / ⚠️ 위험·안티패턴 / 🧹 정리. 영향도: 높음/중간/낮음.
> 수정 성격: **동작변경**(behavior-changing) / **동작보존**(behavior-preserving).
> 수정 전에는 **현재 동작을 고정하는 characterization 테스트**를 먼저 작성한다.

## common-util 신규 발견 (모노레포 목록에 아직 없음)

| # | 위치 | 증상 | 영향 | 수정 성격 |
|---|------|------|------|----------|
| CU1 | `util/CryptoUtils.java` — `md5()` / `hashPassword()` | 🐞 **비밀번호를 MD5 + 무 salt 로 해시**한다. MD5 는 암호학적으로 깨진 알고리즘이고 salt 가 없어 레인보우 테이블·동일 비밀번호 노출에 취약하다. 코드 주석도 "낡았고 취약" 이라 인정한다. | 높음 | 동작변경(해시 교체 — 기존 저장값 마이그레이션 필요) |
| CU2 | `util/JsonUtils.java` — `toJson()` / `fromJson()` | ⚠️ **오류 처리가 비일관.** `toJson` 은 직렬화 예외를 삼키고 `null` 을 반환하는데, `fromJson` 은 `RuntimeException` 으로 던진다. 호출부가 두 메서드를 같은 방식으로 다룰 수 없다. | 중간 | 동작보존(정책 통일 — 호출부 영향 점검) |
| CU3 | `util/ValidationUtils.java` — `EMAIL` / `PHONE` 정규식 | ⚠️ `EMAIL` 정규식이 단순/과허용(예: 로컬파트 검증 약함)이고, `PHONE` 은 한국 휴대폰(`01x`) 전용으로 하드코딩돼 있다. 국제 번호·유선은 통과하지 못한다. | 낮음 | 동작변경(정규식 강화 시 통과 집합 변화) |

> CU1 은 비밀번호(가장 민감한 데이터)가 약한 해시로 저장된다는 점에서 우선순위가 높다. CU1~CU3 모두
> 모노레포 [`../../docs/known-issues.md`](../../docs/known-issues.md) 에 정식 편입할 후보이며, 현재는 이
> 모듈 문서에만 기록한다. 수정은 별도 패스에서 테스트 선행으로 진행한다.

## 모노레포 목록 중 common-util 해당 항목 (참조)

코드 체계는 [`../../docs/known-issues.md`](../../docs/known-issues.md) 와 동일하다. 상세·배경은 링크 참조.

| # | 위치 | 내용 | 수정 성격 |
|---|------|------|----------|
| B3 | `util/MoneyUtils.java` — `round()` | `Math.floor` 로 **버림**한다(주석은 "반올림 의도" 명시). 모든 금액 계산이 이 함수를 거친다. [ADR-0003](../../docs/adr/0003-money-as-double.md) | 동작변경 |
| B7 | `util/DateUtils.java` — `now()` / `localToday()` | 주문 시각은 `now()`=**UTC** 로 저장되는데 집계는 `LocalDate.now()`=**서버 로컬**로 비교 → 날짜 경계에서 누락/중복. `batch/job/DailySalesAggregationJob` 와 짝. | 동작변경 |
| R3 | `util/DateUtils.java` — static `SDF` | `SimpleDateFormat` 을 `static` 공유 → **thread-unsafe**. `DateTimeFormatter` 로 교체 권장. | 동작보존 |
| C3 | `build.gradle` — `commons-lang3:3.12.0` | 오래된 의존성 버전(BOM 밖이라 직접 박힘). | 동작보존(업그레이드) |
| C4 | `util/DateUtils.java` — `parse()` | `ParseException` 을 삼키고 `null` 반환. 호출부 NPE 위험. | 동작보존(+오류 전파/명시) |

## 연관 (모듈 외부지만 동작에 영향)

- **B7** 은 이 모듈 단독 문제가 아니다. 주문 저장은 `DateUtils.now()`(UTC), 일일 집계는
  `batch/job/DailySalesAggregationJob` 의 `LocalDate.now()`(서버 로컬)로 비교하므로 두 곳을 함께 봐야 한다.
- 금액 `double`/버림(**B3**)은 전사 공통 사안이다. 근본 해결은 `double → BigDecimal` 전환이며
  DTO·엔티티·DB 컬럼까지 파급된다([ADR-0003](../../docs/adr/0003-money-as-double.md), 모노레포 "대형 과제").
- `StringUtils` 의 commons-lang3 재구현은 별도 코드 항목은 아니지만, 컨벤션 위반(재발명)으로
  [`code-conventions.md`](./code-conventions.md) "재사용 규칙" 에 기록돼 있다.
