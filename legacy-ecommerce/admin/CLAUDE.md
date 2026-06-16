# admin

> legacy-shop 의 **관리자 API** 모듈. 자체 DB 없이 ecommerce/payment 서비스를 HTTP 로 호출하는
> 얇은 **게이트웨이**다(`:8083`). 전체 시스템 맥락·기술부채 백로그는 모노레포 문서 [`../docs/`](../docs/) 를 본다.

## 정체성

- Java 21 / Spring Boot 3.5.15 / Gradle. 의존성은 `spring-boot-starter-web` **only** (+ `core-framework`, `common-util`).
- 도메인 모델·JPA·DB 가 **없다**. 상태를 들고 있지 않은 요청-응답 프록시.
- 다운스트림(ecommerce `:8081`, payment `:8082`)을 `ShopGateway`(RestTemplate)로 호출한다.

## 빌드 / 실행 / 테스트

프로젝트 루트(`legacy-ecommerce/`)에서 실행한다. **Windows PowerShell**은 `.\gradlew.bat`,
Bash 도구(POSIX 셸)는 `./gradlew`.

```powershell
.\gradlew.bat :admin:build      # 빌드
.\gradlew.bat :admin:bootRun    # 실행 (:8083)
.\gradlew.bat :admin:test       # 테스트 — AdminRefundControllerTest (A1 인증 회귀, 3개) + AdminPriceCalculatorTest (R6 회귀: MoneyUtils 반올림, 3개)
```

> ⚠️ `:admin:bootRun` 전에 **ecommerce(:8081)·payment(:8082) 가 먼저 떠 있어야** 한다.
> admin 은 두 서비스를 HTTP 로 호출하므로 단독 기동만으로는 대부분 엔드포인트가 동작하지 않는다.

## 구조 한눈에

```
src/main/java/com/legacy/shop/admin/
├── AdminApplication.java        @SpringBootApplication(scanBasePackages="com.legacy.shop")
├── web/                         REST 컨트롤러 (얇게 유지 → 게이트웨이 위임)
│   ├── AdminProductController   /admin/products
│   ├── AdminOrderController     /admin/orders
│   └── AdminRefundController    /admin/refunds
├── client/ShopGateway           ecommerce/payment HTTP 호출 (RestTemplate + raw Map)
├── security/AdminAuth           X-Admin-Token 검증
├── util/AdminPriceCalculator    금액 미리보기 계산 (✅ R6 — MoneyUtils 위임, PricingService 와 같은 규칙)
├── config/RestTemplateConfig    RestTemplate 빈 (✅ R8 — connect 2s/read 5s 타임아웃)
└── dto/RefundCommand            환불 요청 record
```

| 메서드 | 경로 | 컨트롤러 | 인증 | 다운스트림 |
|--------|------|----------|------|-----------|
| GET | `/admin/products` | `AdminProductController.list` | ✅ `X-Admin-Token` | ecommerce `GET /api/products` |
| POST | `/admin/products` | `AdminProductController.create` | ✅ | ecommerce `POST /api/products` |
| GET | `/admin/orders/{id}` | `AdminOrderController.get` | ✅ | ecommerce `GET /api/orders/{id}` |
| GET | `/admin/orders/preview-total` | `AdminOrderController.previewTotal` | ✅ | (로컬 `AdminPriceCalculator`) |
| POST | `/admin/refunds` | `AdminRefundController.refund` | ✅ `X-Admin-Token` (A1 ✅ 수정됨) | payment `POST /api/payments/refund` |

## 이 모듈에서 일할 때 주의점

- **인증은 컨트롤러에서 명시적으로** 한다. 보호 엔드포인트는 `@RequestHeader("X-Admin-Token")` 을
  받아 `adminAuth.check(token)` 를 호출한다. 새 엔드포인트도 이 패턴을 **반드시** 따른다.
- ✅ `POST /admin/refunds` 의 무인증 결함(**A1**)은 **2026-06-15 수정됨** — 이제 다른 컨트롤러와 동일하게
  `AdminAuth.check(token)` 로 `X-Admin-Token` 을 검사한다. 회귀 테스트 `AdminRefundControllerTest`
  (모노레포 [`../docs/known-issues.md`](../docs/known-issues.md) **A1**). admin 의 모든 보호 엔드포인트는 이 패턴을 따른다.
- ⚠️ 아래는 모노레포 known-issues 에 등록된 안티패턴이다. **새 코드에서 모방하지 말고**, 손대는 김에 개선한다.
  - 게이트웨이가 타입 DTO 없이 raw `Map` 으로 통신(R2) — [`../docs/known-issues.md`](../docs/known-issues.md), [ADR-0005](../docs/adr/0005-map-based-inter-service-http.md)
  - 서비스 URL·`admin.token`(`admin-secret`) 하드코딩(R5) — 운영은 환경변수/프로파일로 외부화
  - ✅ **R6 — 계산식 복붙 수정됨(2026-06-16)**: `AdminPriceCalculator` 가 ecommerce `PricingService` 계산식을
    복붙(자체 `Math.floor`)하던 것을 **공용 `MoneyUtils.taxOf`/`MoneyUtils.round`(HALF_UP) 위임**으로 교체 →
    PricingService 와 같은 규칙을 공유하고 B3(반올림) 분기도 해소(미리보기=실제). 회귀 `AdminPriceCalculatorTest`.
    새 금액 계산도 복제하지 말고 `MoneyUtils` 를 쓴다.
  - ✅ **R8 — 타임아웃 미설정 수정됨(2026-06-16)**: `RestTemplateConfig` 에 `SimpleClientHttpRequestFactory` 로
    connect 2s/read 5s 타임아웃을 둠 → 다운스트림 지연 시 무한 대기 대신 끊긴다.
- 금액은 `double` 로 다루며 `AdminPriceCalculator` 는 ✅ `MoneyUtils.round`(HALF_UP 반올림)로 정리한다(R6 수정; 이전 `Math.floor`).
  배경은 [ADR-0003](../docs/adr/0003-money-as-double.md).
- 응답은 `ApiResponse<T>` 로 감싸고, 오류는 `throw new BusinessException(ErrorCode.XXX)` 로 던진다
  (둘 다 `core-framework` 제공).

## 더 읽기

모노레포 공통 문서: [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) · [`../docs/known-issues.md`](../docs/known-issues.md)(admin 항목 **A1**·R2·R5·R6·R8) · [`../docs/adr/`](../docs/adr/)
