# common-util

> legacy-shop 의 **공용 정적 유틸 라이브러리**. 금액·날짜·문자열·암호·JSON·검증을 다루는 순수 Java
> 헬퍼 모음으로, Spring·web·DB·엔드포인트가 **없다**. 의존 그래프 **최하단**이라 다른 모든 모듈
> (`core-framework`/`ecommerce`/`payment`/`admin`/`batch`)이 이걸 의존한다. 전체 시스템 맥락은
> 모노레포 문서 [`../docs/`](../docs/) 를 본다(기술부채 백로그 포함).

## 정체성

- Java 21 / Gradle. **순수 Java 라이브러리** — Spring 의존 없음, 실행 대상 아님(`application.yml`·포트 없음).
- 외부 의존성 단 2개: `commons-lang3:3.12.0`(버전 직접 박음, 낡음 — C3), `jackson-databind`(BOM).
- 내부 모듈 의존 **없음**. 반대로 거의 모든 모듈이 이 라이브러리를 쓰므로 **변경 파급이 가장 크다**.
- 패키지 `com.legacy.shop.common.util` 아래 정적 유틸 클래스 6개. 전부 `private` 생성자(인스턴스화 차단).

## 빌드 / 테스트

프로젝트 루트(`legacy-ecommerce/`)에서 실행한다. **Windows PowerShell**은 `.\gradlew.bat`,
Bash 도구(POSIX 셸)는 `./gradlew`.

```powershell
.\gradlew.bat :common-util:build    # 빌드 (라이브러리 jar)
.\gradlew.bat :common-util:test     # 테스트 — MoneyUtilsTest·CryptoUtilsTest·JsonUtilsTest
```

> 실행(`bootRun`) 대상이 아니다. 다른 모듈에 링크되는 라이브러리 jar 일 뿐이다.

## 구조 한눈에

```
src/main/java/com/legacy/shop/common/util/
├── MoneyUtils       금액 계산  — round(HALF_UP)/applyTax/taxOf/multiply/discount/format, 상수 TAX_RATE=0.1
├── DateUtils        날짜/시각  — format/parse/today/now(UTC)/localToday(서버로컬), 불변 DateTimeFormatter
├── StringUtils      문자열     — isEmpty/isBlank/nvl/maskCard/join
├── CryptoUtils      해시       — hashPassword/verifyPassword/needsRehash (PBKDF2+salt; md5 레거시 폴백)
├── JsonUtils        JSON       — toJson/fromJson(둘 다 실패 시 JsonException), static ObjectMapper
└── ValidationUtils  검증       — isEmail/isPhone(한국 01x)/isPositive
```

| 클래스 | 책임 | 알아둘 점 |
|--------|------|---------|
| `MoneyUtils` | 금액 계산(`BigDecimal`) | 전 메서드 `BigDecimal`(scale 2/HALF_UP). rate(할인율 등 무차원 계수) 인자는 `double` 유지 |
| `DateUtils` | 날짜·시각 변환 | `now()`=UTC, `localToday()`=서버로컬(달력 날짜용). thread-safe 불변 `DateTimeFormatter`(R3 ✅), `parse()` 는 형식 오류 시 `DateTimeParseException` fail-fast(C4 ✅) |
| `StringUtils` | 문자열 보조 | commons-lang3 기능 재구현(중복) — 새 코드는 commons-lang3 사용 |
| `CryptoUtils` | 비밀번호 해시 | PBKDF2+임의 salt(레거시 MD5 검증 폴백). `hashPassword(null)` 은 `IllegalArgumentException` |
| `JsonUtils` | 직렬화/역직렬화 | 오류는 fail-fast — `toJson`/`fromJson` 둘 다 실패 시 `JsonException` |
| `ValidationUtils` | 형식 검증 | ⚠️ 정규식 단순/한국 전용(CU3) — `EMAIL` 과허용, `PHONE` 은 `01x` 만 |

## 이 모듈에서 일할 때 주의점

- **정적 유틸 클래스 패턴**을 유지한다: `private` 생성자로 인스턴스화를 막고 멤버는 전부 `static`,
  클래스 상단에 한 줄 `/** ... */` 역할 주석. 새 유틸도 이 형태로 추가한다.
- **재발명 금지**: 이미 `commons-lang3` 와 jackson 을 의존한다. `StringUtils` 가 `isEmpty`/`isBlank`
  같은 commons-lang3 기능을 다시 만든 사례가 있으니 모방하지 말고 기존 라이브러리를 쓴다.
- **금액은 `MoneyUtils` 에 모은다.** 전 메서드가 `BigDecimal`(scale 2/HALF_UP)을 받고 돌려준다 — 새 금액
  계산을 복제하지 말고 여기에 추가하며, 비교는 `compareTo`(scale 민감 `equals` 금지). 비율 인자만 `double`
  ([ADR-0006](../docs/adr/0006-money-as-bigdecimal.md)).
- ⚠️ 아래는 아직 남은 결함이다. **새 코드에서 모방하지 말고**, 손대는 김에 (테스트 선행 후) 개선한다.
  - `DateUtils`: `now()`=UTC 와 `localToday()`=서버로컬을 **혼용하지 말 것**(달력 날짜엔 `localToday()`,
    UTC 주문 시각 집계엔 UTC 기준 — B7 참고). 포매터는 불변 `DateTimeFormatter`(thread-safe, R3 ✅),
    `parse()` 는 형식 오류 시 `DateTimeParseException` fail-fast(C4 ✅) — `null` 을 반환하지 않는다.
  - `ValidationUtils`: `EMAIL` 정규식 과허용, `PHONE` 한국 `01x` 전용(국제·유선 불통과) — CU3.
  - `commons-lang3:3.12.0` 버전 직접 박음(낡음) — C3.
  - 코드·상세는 모노레포 [`../docs/known-issues.md`](../docs/known-issues.md).

## 더 읽기

모노레포 공통 문서: [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) · [`../docs/known-issues.md`](../docs/known-issues.md) · [`../docs/adr/`](../docs/adr/)
