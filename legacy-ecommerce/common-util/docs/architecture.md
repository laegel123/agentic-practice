# common-util 아키텍처

> 이 문서는 `common-util` 모듈 **내부** 구조에 집중한다. 모듈 간 의존 그래프·전체 그림은
> 모노레포 [`../../docs/architecture.md`](../../docs/architecture.md) 를 본다.

## 책임

전 모듈이 공유하는 **정적 유틸 계층**. 금액·날짜·문자열·암호·JSON·검증 같은 잡다한 헬퍼를
한 군데에 모은다. 도메인 모델·상태·프레임워크(Spring)·DB·엔드포인트가 **없는** 순수 Java 라이브러리다.

의존 그래프상 **최하단**에 있다. 이 라이브러리는 다른 어떤 내부 모듈도 의존하지 않고, 반대로
`core-framework`·`ecommerce`·`payment`·`admin`·`batch` 가 모두 이걸 의존한다. 따라서 시그니처/동작을
바꾸면 **모든 모듈로 파급**된다 — 변경 시 영향 범위를 항상 의식한다.

## 패키지 / 계층

공통 컨벤션의 `web / service / domain / repository` 계층이 **없다**(상태도 영속성도 없으므로).
패키지는 `com.legacy.shop.common.util` 하나뿐이고, 그 아래 정적 유틸 클래스 6개가 평평하게 놓인다.

```
com.legacy.shop.common.util
├── MoneyUtils
├── DateUtils
├── StringUtils
├── CryptoUtils
├── JsonUtils
└── ValidationUtils
```

호출 방향은 단방향이다 — 상위 모듈이 이 클래스들의 `static` 메서드를 직접 호출할 뿐, 이 모듈이
바깥을 호출하지 않는다. 유틸 간 의존도 거의 없다(예외: `MoneyUtils.applyTax/taxOf/discount` 가
내부에서 `round` 를 호출).

## 클래스 인벤토리

| 클래스 | 역할 | 주요 메서드 (시그니처) | 비고 |
|--------|------|------------------------|------|
| `MoneyUtils` | 금액 계산(`double`) | `round(double)`, `applyTax(double)`, `taxOf(double)`, `multiply(double, int)`, `discount(double, double)`, `format(double)`; 상수 `TAX_RATE=0.1` | ⚠️ `round` 는 `Math.floor` **버림**(B3). `applyTax/taxOf/discount` 가 `round` 경유 |
| `DateUtils` | 날짜·시각 변환 | `format(Date)`, `parse(String)`, `today()`, `now()`, `localToday()` | ⚠️ `now()`=UTC `LocalDateTime`, `localToday()`=서버로컬 `LocalDate` 혼용(B7). `parse()` 는 실패 시 `null`(C4) |
| `StringUtils` | 문자열 보조 | `isEmpty(String)`, `isBlank(String)`, `nvl(String, String)`, `maskCard(String)`, `join(List<String>, String)` | ⚠️ `isEmpty`/`isBlank` 등 commons-lang3 기능 재구현. `join` 도 표준 API 로 대체 가능 |
| `CryptoUtils` | 해시 | `md5(String)`, `hashPassword(String)` | ⚠️ MD5 + 무 salt 비밀번호 해시(CU1). 알고리즘 없을 때만 `RuntimeException` |
| `JsonUtils` | JSON 직렬화/역직렬화 | `toJson(Object)`, `<T> T fromJson(String, Class<T>)` | ⚠️ `toJson` 은 예외 삼키고 `null`, `fromJson` 은 `RuntimeException` — 오류 처리 비일관(CU2) |
| `ValidationUtils` | 형식 검증 | `isEmail(String)`, `isPhone(String)`, `isPositive(int)` | ⚠️ `EMAIL` 정규식 단순/과허용, `PHONE` 은 한국 01x 전용(CU3) |

## 상태와 스레드 안전성

순수 유틸이지만 일부 클래스가 `static` 공유 필드를 들고 있다. 스레드 안전성이 갈린다.

| 필드 | 타입 | 스레드 안전 | 비고 |
|------|------|------------|------|
| `JsonUtils.MAPPER` | `ObjectMapper` | ✅ 안전 | jackson `ObjectMapper` 는 설정 후 공유가 권장되는 패턴 |
| `ValidationUtils.EMAIL` / `PHONE` | `Pattern` | ✅ 안전 | `java.util.regex.Pattern` 은 불변·thread-safe |
| `MoneyUtils.TAX_RATE` | `double` 상수 | ✅ 안전 | `final` 상수 |
| ⚠️ `DateUtils.SDF` | `SimpleDateFormat` | ❌ **불안전** | `SimpleDateFormat` 은 thread-safe 하지 않은데 `static` 으로 공유한다(R3). 동시 호출 시 깨질 수 있다 |

> `DateUtils.today()` 는 호출마다 새 `SimpleDateFormat` 인스턴스를 만들어 이 문제를 피한다. 반면
> `format(Date)`/`parse(String)` 은 공유 `SDF` 를 쓴다(R3 영향 범위).

## 의존성 / 빌드

```gradle
// common-util/build.gradle
dependencies {
    implementation 'org.apache.commons:commons-lang3:3.12.0'   // 버전 직접 박음 (낡음 — C3)
    implementation 'com.fasterxml.jackson.core:jackson-databind' // BOM 버전 사용
}
```

- **외부 의존성 2개뿐**: `commons-lang3`(버전 하드코딩 — BOM 밖이라 직접 명시), jackson(Boot BOM 관리).
  버전 정책 배경은 [ADR-0004](../../docs/adr/0004-no-gradle-version-catalog.md).
- **내부 의존성 없음**. 역으로 `core-framework`·`ecommerce`·`payment`·`admin`·`batch` 가 이걸 의존한다.
- 빌드 산출물은 라이브러리 jar 다. 실행(`bootRun`) 대상이 아니다. 빌드는
  `.\gradlew.bat :common-util:build`.
