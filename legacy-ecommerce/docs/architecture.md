# 아키텍처

> legacy-shop 의 구조, 모듈 책임, 데이터 흐름, 서비스 간 통신을 설명한다.
> 결정의 "왜"는 [ADR](./adr/)에, 알려진 문제는 [known-issues.md](./known-issues.md)에 정리되어 있다.

## 한눈에 보기

Java 21 / Spring Boot 3.5.15 기반의 **멀티모듈 Gradle 프로젝트**. 진짜 마이크로서비스가 아니라,
하나의 코드베이스를 책임별로 수평 분리한 **모듈형 모놀리스**다. 4개의 독립 실행 가능한 Spring Boot
앱(ecommerce / payment / admin / batch)이 2개의 라이브러리 모듈(core-framework / common-util)을
공유한다.

## 모듈 의존 그래프

```
                 common-util  (순수 Java: 암호/날짜/금액/문자열/JSON 유틸)
                      ▲
                      │
              core-framework  (BaseTimeEntity, ApiResponse, ErrorCode,
                      ▲        BusinessException, GlobalExceptionHandler, PageRequestDto)
                      │
   ┌──────────────────┼──────────────────┬──────────────────┐
   │                  │                  │                  │
ecommerce-service  payment-service     admin              batch
   :8081              :8082             :8083            (1회 실행)
```

- 모든 실행 앱은 `core-framework` 와 `common-util` 에 의존한다.
- `core-framework` 는 `common-util` 에 의존한다.
- 모듈 간 의존은 단방향이며 순환이 없다.

| 모듈 | 타입 | 주요 의존성 | 책임 |
|------|------|------------|------|
| `common-util` | 순수 Java 라이브러리 | commons-lang3:3.12.0(하드코딩), jackson(BOM) | 정적 유틸: `MoneyUtils`, `DateUtils`, `StringUtils`, `CryptoUtils`, `JsonUtils`, `ValidationUtils` |
| `core-framework` | Spring 라이브러리(부트 아님) | spring-context/web/data-jpa, jakarta.persistence | 전사 공통 기반 클래스. 부트 앱이 `scanBasePackages="com.legacy.shop"` 로 함께 스캔한다 |
| `ecommerce-service` | Spring Boot 앱 :8081 | web, data-jpa, validation, h2 | 상품/카테고리/재고/장바구니/주문/쿠폰/고객. **스키마 소유자** |
| `payment-service` | Spring Boot 앱 :8082 | web, data-jpa, h2 | 결제/환불/결제수단/원장(Ledger) |
| `admin` | Spring Boot 앱 :8083 | web only | 관리자 API. 자체 DB 없이 ecommerce/payment 를 HTTP 호출 |
| `batch` | Spring Boot 앱(웹 없음) | starter, data-jpa, h2 | 정산/일일매출집계/재고대사/장바구니정리. 실행 시 1회 돌고 종료 |

## 데이터베이스 토폴로지

H2 파일 DB를 쓰며, **2개의 물리 DB**가 존재한다.

```
~/legacyshopdb   ← ecommerce-service (ddl-auto: update, 스키마 생성)
                 ← batch             (ddl-auto: none, 같은 DB를 읽음 = 공유DB)

~/legacypaydb    ← payment-service   (ddl-auto: update, 독립 DB)
```

- `ecommerce-service` 가 `legacyshopdb` 의 스키마를 만들고, `batch` 는 같은 파일을 **공유**해서 읽는다
  (배치는 `ddl-auto: none`). 따라서 배치는 이커머스가 먼저 떠서 스키마를 만든 뒤에야 정상 동작한다.
- `payment-service` 는 `legacypaydb` 라는 **별도** DB를 쓴다. 이커머스/배치 DB와 분리되어 있다.
- 접속 URL은 모두 `AUTO_SERVER=TRUE` 라서 여러 프로세스가 동시에 같은 파일 DB에 붙을 수 있다.
- H2 콘솔: 각 웹 앱의 `/h2-console` (user `sa`, password 없음). 자세한 배경은 [ADR-0002](./adr/0002-shared-h2-file-database.md).

## 주문 처리 흐름 (핵심 시나리오)

`POST /api/orders` → `OrderService.placeOrder()` 한 메서드가 7단계를 한 트랜잭션에서 처리한다
(이른바 "God method", [ADR 없음 — 리팩토링 대상](./known-issues.md)).

```
1. 재고 확인 + 예약(차감)      InventoryService.checkStock / reserve
2. 주문/주문아이템 생성        Order, OrderItem
3. 쿠폰 적용 + 금액 계산        CouponService.getValidCoupon → PricingService.calculate
4. 결제 호출 (HTTP)            PaymentClient.charge ──REST──▶ payment-service :8082
5. 재고 확정                   InventoryService.confirm (검증만, 차감 없음 — B1 ✅)
6. 장바구니 비우기             Cart.clear
7. 주문완료 알림               (현재는 System.out.println)
```

> ✅ B1 수정됨(2026-06-16): (이전) 1단계 `reserve()` 와 5단계 `confirm()` 가 **둘 다 재고를 차감**해
> 주문 1건당 재고가 2배 빠졌다. 이제 `confirm()` 은 검증만 하고 차감은 `reserve()` 에서 1회만 일어난다.
> [known-issues.md](./known-issues.md) B1 참고.

금액 계산 순서는 **소계 → 할인 → 세금 → 합계**이며, 세금은 소계 기준 10%다(`PricingService`).
모든 금액은 `double` 로 다루고 `MoneyUtils.round()` 로 정리한다(현재 반올림이 아니라 **버림**으로 동작).
배경은 [ADR-0003](./adr/0003-money-as-double.md).

## 서비스 간 통신

```
admin :8083 ──RestTemplate(raw Map)──▶ ecommerce :8081   (상품/주문 조회·생성)
            └─RestTemplate(raw Map)──▶ payment   :8082   (환불)

ecommerce :8081 ─RestTemplate(raw Map)─▶ payment :8082   (결제 charge / refund)
```

- HTTP 클라이언트는 `ecommerce-service` 의 `PaymentClient`, `admin` 의 `ShopGateway` 두 곳.
- 둘 다 요청/응답을 **타입 DTO 없이 `Map` 으로 주고받는다**(`Map.class` 역직렬화 후 캐스팅).
  배경과 리스크는 [ADR-0005](./adr/0005-map-based-inter-service-http.md).
- 호출 대상 URL은 `@Value` 기본값으로 하드코딩되어 있다(`http://localhost:8081` 등).
- 인증: `admin` API는 `X-Admin-Token` 헤더를 `AdminAuth` 가 검증한다(토큰 기본값 `admin-secret` 하드코딩).

## REST API 표면 (요약)

모든 응답은 공통 봉투 `ApiResponse<T> = { code, message, data }` 로 감싼다(성공 시 `code="0000", message="OK"`).

**ecommerce-service (:8081)**
- `GET  /api/products` (페이징), `GET /api/products/{id}`, `GET /api/products/{id}/stock`,
  `GET /api/products/search?keyword=`, `POST /api/products`
- `POST /api/cart/...` (장바구니 담기/조회 — `CartController`)
- `POST /api/orders` (주문 생성), `GET /api/orders/{id}`, `GET /api/orders?customerId=`

**payment-service (:8082)**
- `POST /api/payments/charge`, `POST /api/payments/refund`, `GET /api/payments/{id}`
- 결제수단: `PaymentMethodController` (`/api/payment-methods` 계열)

**admin (:8083)** — `X-Admin-Token` 필요
- 상품/주문/환불 관리 (`AdminProductController`, `AdminOrderController`, `AdminRefundController`)

## 실행 방법

Windows PowerShell 에서는 `.\gradlew.bat`, POSIX 셸(Bash 도구)에서는 `./gradlew` 를 쓴다.

```powershell
.\gradlew.bat build                       # 전체 빌드
.\gradlew.bat :ecommerce-service:bootRun   # :8081 (스키마 생성 — 가장 먼저 실행)
.\gradlew.bat :payment-service:bootRun     # :8082
.\gradlew.bat :admin:bootRun               # :8083 (ecommerce/payment 가 떠 있어야 함)
.\gradlew.bat :batch:bootRun               # 잡 1회 실행 후 종료 (ecommerce DB 스키마 필요)
```

권장 기동 순서: **ecommerce → payment → (admin / batch)**. 이커머스가 `legacyshopdb` 스키마를
만든 뒤라야 batch 와 admin 이 정상 동작한다.

## 빌드 구성 메모

- 루트 `build.gradle` 의 `subprojects` 블록이 Java 21, UTF-8 인코딩, Spring Boot BOM(`dependencyManagement`),
  `test { useJUnitPlatform() }` 를 모든 모듈에 공통 적용한다.
- Spring Boot 플러그인은 루트에서 `apply false` 로 선언하고, 실행 앱 모듈에서만 `id 'org.springframework.boot'` 로 적용한다.
- 버전 카탈로그(`libs.versions.toml`)는 쓰지 않는다 — 의도적 결정. [ADR-0004](./adr/0004-no-gradle-version-catalog.md).
- **테스트**: `common-util`/`core-framework`/`ecommerce-service`/`payment-service`/`admin`/`batch` 전 모듈에 테스트가 있다(전체 57개,
  JUnit5 + Mockito + AssertJ; 의존성은 각 모듈 `build.gradle`의 `testImplementation`). characterization +
  버그수정 회귀(B1·B2·B3·B4·B5·B6·B7·BT1) + 보안 회귀(E1·A1·CU1). 실행은 `./gradlew test`, 인메모리 H2 프로파일(`test`)로
  실 파일 DB와 격리된다. `core-framework` 는 순수 POJO 검증용 `PageRequestDtoTest`(B5)로 첫 테스트가 생겼다.
