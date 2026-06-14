# common-util

> legacy-shop 의 **공용 정적 유틸 라이브러리**. 금액·날짜·문자열·암호·JSON·검증을 다루는 순수 Java
> 헬퍼 모음으로, Spring·web·DB·엔드포인트가 **없다**. 의존 그래프 **최하단**이라 다른 모든 모듈
> (`core-framework`/`ecommerce`/`payment`/`admin`/`batch`)이 이걸 의존한다. 전체 시스템 맥락은
> 모노레포 문서 [`../docs/`](../docs/) 를, 이 모듈의 상세는 [`docs/`](./docs/) 를 본다.

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
.\gradlew.bat :common-util:test     # 테스트 — 현재 테스트 소스 없음
```

> 실행(`bootRun`) 대상이 아니다. 다른 모듈에 링크되는 라이브러리 jar 일 뿐이다.

## 구조 한눈에

```
src/main/java/com/legacy/shop/common/util/
├── MoneyUtils       금액 계산  — round/applyTax/taxOf/multiply/discount/format, 상수 TAX_RATE=0.1
├── DateUtils        날짜/시각  — format/parse/today/now(UTC)/localToday(서버로컬), static SDF
├── StringUtils      문자열     — isEmpty/isBlank/nvl/maskCard/join
├── CryptoUtils      해시       — md5/hashPassword  (⚠ MD5·무 salt)
├── JsonUtils        JSON       — toJson/fromJson, static ObjectMapper
└── ValidationUtils  검증       — isEmail/isPhone(한국 01x)/isPositive
```

| 클래스 | 책임 | ⚠️ 함정 |
|--------|------|---------|
| `MoneyUtils` | 금액 계산(`double`) | `round()` 가 `Math.floor` **버림**(B3) |
| `DateUtils` | 날짜·시각 변환 | `now()`=UTC vs `localToday()`=서버로컬 혼용(B7), static `SDF` thread-unsafe(R3), `parse()` null 삼킴(C4) |
| `StringUtils` | 문자열 보조 | commons-lang3 기능 재구현(중복) |
| `CryptoUtils` | 비밀번호 해시 | MD5 + 무 salt(CU1) |
| `JsonUtils` | 직렬화/역직렬화 | 오류 처리 비일관(CU2) |
| `ValidationUtils` | 형식 검증 | 정규식 단순/한국 전용(CU3) |

## 이 모듈에서 일할 때 주의점

- **정적 유틸 클래스 패턴**을 유지한다: `private` 생성자로 인스턴스화를 막고 멤버는 전부 `static`,
  클래스 상단에 한 줄 `/** ... */` 역할 주석. 새 유틸도 이 형태로 추가한다.
- **재발명 금지**: 이미 `commons-lang3` 와 jackson 을 의존한다. `StringUtils` 가 `isEmpty`/`isBlank`
  같은 commons-lang3 기능을 다시 만든 사례가 있으니 모방하지 말고 기존 라이브러리를 쓴다.
- ⚠️ 아래는 알려진 결함이다. **새 코드에서 모방하지 말고**, 손대는 김에 (테스트 선행 후) 개선한다.
  - `MoneyUtils.round()` 이름과 달리 **버림**(B3) — 모든 금액 계산이 이 함수를 거친다.
  - `DateUtils` UTC/로컬 혼용(B7) · static `SimpleDateFormat`(R3) · `parse()` null 반환(C4).
  - `CryptoUtils` MD5+무 salt 비밀번호 해시(CU1), `JsonUtils` 오류 처리 비일관(CU2).
  - 코드·상세는 [`docs/known-issues.md`](./docs/known-issues.md).
- 금액은 전사적으로 `double` 로 다룬다(배경 [ADR-0003](../docs/adr/0003-money-as-double.md)). 새 금액 계산은
  복제하지 말고 `MoneyUtils` 에 모은다(`admin` 의 `AdminPriceCalculator` 복붙 사례 R6 참고).

## 더 읽기

- 이 모듈: [`docs/architecture.md`](./docs/architecture.md) · [`docs/code-conventions.md`](./docs/code-conventions.md) · [`docs/known-issues.md`](./docs/known-issues.md)
- 모노레포 공통: [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) · [`../docs/known-issues.md`](../docs/known-issues.md) · [`../docs/adr/`](../docs/adr/)
