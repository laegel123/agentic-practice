# common-util 코드 컨벤션

> 이 모듈은 모노레포 공통 컨벤션 [`../../docs/code-conventions.md`](../../docs/code-conventions.md) 을
> 그대로 따른다(패키지 루트 `com.legacy.shop`, 한국어 주석/영어 식별자, BOM 기반 버전 선언 등).
> 단 이 모듈은 **순수 Java 라이브러리**라 Spring 관련 규칙(생성자 주입, 엔티티, `ApiResponse`/
> `BusinessException`, DTO=`record`)은 **적용되지 않는다**. 아래에는 **common-util 에 한정된
> 보충·차이점만** 적는다.

## 구조상의 차이

- **정적 유틸 라이브러리**라 공통 컨벤션의 `web / service / domain / repository` 계층이 **없다**.
- **Spring 빈·DI 가 없다**. `@Service`/`@Component`/생성자 주입을 쓰지 않는다 — 전부 `static` 호출.
- 패키지는 `com.legacy.shop.common.util` **하나**뿐이고, 그 아래 유틸 클래스가 평평하게 놓인다.

## 정적 유틸 클래스 패턴 (이 모듈의 핵심 규칙)

모든 유틸은 인스턴스를 만들지 않는다. `private` 생성자로 인스턴스화를 막고 멤버는 전부 `static`,
클래스 상단에 한 줄 `/** ... */` 역할 주석을 둔다.

```java
package com.legacy.shop.common.util;

/**
 * 금액 계산 유틸. 시스템 전반에서 금액을 double 로 다룬다.
 */
public class MoneyUtils {

    public static final double TAX_RATE = 0.1;   // 공유 상수는 UPPER_SNAKE

    private MoneyUtils() {                        // 인스턴스화 차단
    }

    public static double round(double amount) {   // 멤버는 전부 static
        return Math.floor(amount * 100) / 100.0;
    }
}
```

- 새 유틸 클래스도 이 형태(`private` 생성자 + `static` 멤버)로 추가한다.
- 공유 상수/정규식/포매터는 `private static final` 으로 두고 UPPER_SNAKE 로 명명(`TAX_RATE`, `EMAIL`,
  `PHONE`, `MAPPER`, `SDF`).
- 참고: 기존 클래스는 `final class` 로 선언돼 있지 **않다**(`private` 생성자로만 인스턴스화를 막는다).
  새 클래스는 `final` 을 붙여도 무방하나, 기존 스타일과의 일관성만 깨지 않으면 된다.

## 네이밍

- 클래스: `*Utils` 접미사(`MoneyUtils`, `DateUtils`, ...).
- 술어(boolean 반환): `is*`(`isEmpty`, `isBlank`, `isEmail`, `isPositive`) 또는 `*Of`(`taxOf`).
- 변환/동작: 동사 선행(`format`, `parse`, `maskCard`, `applyTax`, `toJson`, `fromJson`, `multiply`, `join`).
- 파라미터: 단순 타입은 짧게(`s`, `n`, `o`), 도메인 값은 의미 있게(`amount`, `cardNo`, `price`, `qty`, `rate`).

## 재사용 규칙

- 금액 계산은 `MoneyUtils`, 시각은 `DateUtils` 등 **한 곳에 모은다**. 같은 계산을 모듈마다 복제하지 않는다.
- ⚠️ **이미 의존하는 라이브러리를 재구현하지 않는다.** 이 모듈은 `commons-lang3` 와 jackson 을
  의존한다. 그런데 `StringUtils.isEmpty/isBlank` 는 `org.apache.commons.lang3.StringUtils` 에 이미
  있는 것을 다시 만든 사례다 — 새 코드에서 따라 하지 말고 기존 라이브러리를 쓴다.
- ⚠️ 모듈 밖에서도 같은 함정이 있다: `admin/util/AdminPriceCalculator` 가 ecommerce `PricingService`
  계산식을 복붙했다(모노레포 **R6**). 금액 계산이 필요하면 복제하지 말고 `MoneyUtils` 를 재사용한다.

## ⚠️ 모방 금지 안티패턴 (이 모듈에 존재)

손대는 김에 (테스트 선행 후) 개선하되, **새 코드에서 그대로 답습하지 않는다.** 코드(B/R/C)는 모노레포
[`../../docs/known-issues.md`](../../docs/known-issues.md) 체계를 그대로 쓰고, CU 코드는 이 모듈
[`known-issues.md`](./known-issues.md) 에 정의돼 있다.

- **B3 — `round` 가 버림**: `MoneyUtils.round()` 가 이름과 달리 `Math.floor` 로 **버림**한다(주석은
  "반올림 의도"). 모든 금액 계산이 이 함수를 거친다. 배경 [ADR-0003](../../docs/adr/0003-money-as-double.md).
- **R3 — thread-unsafe `SimpleDateFormat`**: `DateUtils.SDF` 를 `static` 공유한다. 새 코드는
  `DateTimeFormatter`(불변·thread-safe)를 쓴다.
- **B7 — UTC/로컬 혼용**: `DateUtils.now()`=UTC, `localToday()`=서버 로컬. 시각/날짜 기준이 섞이면
  날짜 경계 버그가 난다. 새 코드는 기준 타임존을 명시한다.
- **C4 — 예외 삼킴**: `DateUtils.parse()` 가 `ParseException` 을 삼키고 `null` 을 반환한다(호출부 NPE 위험).
- **CU1 — 약한 비밀번호 해시**: `CryptoUtils` 가 MD5 + 무 salt. 새 인증 코드에서 답습 금지.
- **CU2 — 비일관 오류 처리**: `JsonUtils.toJson` 은 `null` 반환, `fromJson` 은 `RuntimeException`.
  새 코드는 한쪽 정책으로 통일한다.

## 주석 / 언어

- 본문 주석·도메인 용어는 한국어, 식별자/타입은 영어(공통 규칙과 동일).
- 클래스 상단에 한 줄 `/** ... */` 로 역할을 적는 기존 스타일을 유지한다. 과도한 주석은 더하지 않는다.
