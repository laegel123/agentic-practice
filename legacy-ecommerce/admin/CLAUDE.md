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
.\gradlew.bat :admin:test       # 테스트 — AdminRefundControllerTest·AdminPriceCalculatorTest·AdminAuthTest
```

> ⚠️ **로컬 실행 시 `ADMIN_TOKEN` 필수**: `$env:ADMIN_TOKEN="dev-token"; .\gradlew.bat :admin:bootRun`
> (또는 `--args='--admin.token=dev-token'`). 미설정/빈 값이면 `AdminAuth` 가 기동을 의도적으로 실패시킨다(fail-closed).
> 또한 `:admin:bootRun` 전에 **ecommerce(:8081)·payment(:8082) 가 먼저 떠 있어야** 한다 — admin 은 두 서비스를
> HTTP 로 호출하므로 단독 기동만으론 대부분 엔드포인트가 동작하지 않는다.

## 구조 한눈에

```
src/main/java/com/legacy/shop/admin/
├── AdminApplication.java        @SpringBootApplication(scanBasePackages="com.legacy.shop")
├── web/                         REST 컨트롤러 (얇게 유지 → 게이트웨이 위임)
│   ├── AdminProductController   /admin/products
│   ├── AdminOrderController     /admin/orders
│   └── AdminRefundController    /admin/refunds
├── client/ShopGateway           ecommerce/payment HTTP 호출 (환불 요청은 타입 record; 조회/생성 패스스루 응답은 Map)
├── security/AdminAuth           X-Admin-Token 검증 (토큰 외부주입·미설정 시 기동 실패·상수시간 비교)
├── util/AdminPriceCalculator    금액 미리보기 계산 (MoneyUtils 위임 — PricingService 와 같은 규칙)
├── config/RestTemplateConfig    RestTemplate 빈 (connect 2s/read 5s 타임아웃)
└── dto/RefundCommand            환불 요청 record
```

| 메서드 | 경로 | 컨트롤러 | 인증 | 다운스트림 |
|--------|------|----------|------|-----------|
| GET | `/admin/products` | `AdminProductController.list` | ✅ `X-Admin-Token` | ecommerce `GET /api/products` |
| POST | `/admin/products` | `AdminProductController.create` | ✅ | ecommerce `POST /api/products` |
| GET | `/admin/orders/{id}` | `AdminOrderController.get` | ✅ | ecommerce `GET /api/orders/{id}` |
| GET | `/admin/orders/preview-total` | `AdminOrderController.previewTotal` | ✅ | (로컬 `AdminPriceCalculator`) |
| POST | `/admin/refunds` | `AdminRefundController.refund` | ✅ `X-Admin-Token` | payment `POST /api/payments/refund` |

## 이 모듈에서 일할 때 주의점

- **인증은 컨트롤러에서 명시적으로** 한다. 보호 엔드포인트는 `@RequestHeader("X-Admin-Token")` 을
  받아 `adminAuth.check(token)` 를 호출한다. 새 엔드포인트도 이 패턴을 **반드시** 따른다.
- **`AdminAuth` 는 fail-closed**: `admin.token`(env `ADMIN_TOKEN`) 미설정/빈 값이면 빈 생성 시
  `IllegalStateException` 으로 기동 실패한다. 토큰 비교는 `MessageDigest.isEqual`(상수시간). 공개 기본값을
  다시 넣지 말 것(fail-open 회귀).
- **금액은 `BigDecimal`(scale 2/HALF_UP)**. `AdminPriceCalculator` 는 자체 산식을 두지 말고
  `MoneyUtils.taxOf`/`round`(HALF_UP)에 위임해 ecommerce `PricingService` 와 규칙을 공유한다
  ([ADR-0006](../docs/adr/0006-money-as-bigdecimal.md)). Spring 이 `@RequestParam BigDecimal` 로 바인딩한다.
- **서비스 간 HTTP**: admin 이 직접 조립하는 요청 바디는 타입 record(`client/dto/*`)로 보낸다. 조회/생성
  패스스루의 **응답은 의도적으로 `Map`/`Object` 유지** — admin 은 도메인 모델 없는 무상태 프록시라, 타입을
  입히면 ecommerce 도메인을 중복 모델링하고 응답 바이트가 바뀐다([ADR-0005](../docs/adr/0005-map-based-inter-service-http.md)).
- 응답은 `ApiResponse<T>` 로 감싸고, 오류는 `throw new BusinessException(ErrorCode.XXX)` 로 던진다.
- ⚠️ 남은 과제: 서비스 URL 하드코딩(R5 — 운영은 환경변수/프로파일로 외부화). 모노레포
  [`../docs/known-issues.md`](../docs/known-issues.md) 참고.

## 더 읽기

모노레포 공통 문서: [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) · [`../docs/known-issues.md`](../docs/known-issues.md) · [`../docs/adr/`](../docs/adr/)
