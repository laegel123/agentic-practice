# admin 아키텍처

> 이 문서는 `admin` 모듈 **내부** 구조에 집중한다. 모듈 간 통신·DB 토폴로지·전체 그림은
> 모노레포 [`../../docs/architecture.md`](../../docs/architecture.md) (특히 "서비스 간 통신" 절)을 본다.

## 책임

관리자용 **상품·주문·환불** 작업을 위한 API. 자체 도메인 모델이나 DB 가 없고,
ecommerce·payment 서비스를 HTTP 로 호출해 결과를 그대로 중계하는 **상태 비저장 게이트웨이**다.
포트는 `:8083`.

> 실제 엔드포인트는 상품·주문·환불 3종뿐이다. `build.gradle` 주석은 "회원 관리"도 언급하지만
> 회원 엔드포인트는 구현돼 있지 않다(코드-주석 불일치).

## 패키지 / 계층

공통 컨벤션의 `service / domain / repository` 계층이 **없다**(소유한 데이터가 없으므로).
대신 이 모듈만의 `security` 패키지가 있다. 흐름은 단방향이다.

```
web (컨트롤러)
  → security (AdminAuth: X-Admin-Token 검증)
  → client (ShopGateway: RestTemplate)
  → (HTTP) ecommerce :8081 / payment :8082
```

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `web` | `AdminProductController`, `AdminOrderController`, `AdminRefundController` | REST 엔드포인트. 얇게 유지하고 게이트웨이에 위임 |
| `client` | `ShopGateway` | 다운스트림 HTTP 호출 (RestTemplate, raw `Map`) |
| `security` | `AdminAuth` | `X-Admin-Token` 헤더 검증 |
| `util` | `AdminPriceCalculator` | 금액 미리보기 계산 (⚠ `PricingService` 복붙) |
| `config` | `RestTemplateConfig` | `RestTemplate` 빈 (⚠ 타임아웃 미설정) |
| `dto` | `RefundCommand` | 환불 요청 `record` |
| (루트) | `AdminApplication` | `@SpringBootApplication(scanBasePackages = "com.legacy.shop")` |

> `scanBasePackages = "com.legacy.shop"` 로 그룹 전체를 스캔해야 `core-framework` 의
> `GlobalExceptionHandler`(`@RestControllerAdvice`) 등 공통 빈이 함께 잡힌다.

## 엔드포인트

| 메서드 | 경로 | 컨트롤러 | 인증 | 다운스트림 |
|--------|------|----------|------|-----------|
| GET | `/admin/products` | `AdminProductController.list` | ✅ | ecommerce `GET /api/products?page&size` |
| POST | `/admin/products` | `AdminProductController.create` | ✅ | ecommerce `POST /api/products` |
| GET | `/admin/orders/{id}` | `AdminOrderController.get` | ✅ | ecommerce `GET /api/orders/{id}` |
| GET | `/admin/orders/preview-total` | `AdminOrderController.previewTotal` | ✅ | (로컬 계산, 외부 호출 없음) |
| POST | `/admin/refunds` | `AdminRefundController.refund` | ⚠️ **없음** | payment `POST /api/payments/refund` |

> ⚠️ `/admin/refunds` 만 인증 검사가 빠져 있다. [`known-issues.md`](./known-issues.md) 참고.

## 요청 흐름 (대표 2개)

**(a) 상품 목록 조회 — `GET /admin/products`**

```
web/AdminProductController.list()
  → security/AdminAuth.check(token)        (실패 시 BusinessException(ErrorCode.UNAUTHORIZED))
  → client/ShopGateway.listProducts(page, size)
      → RestTemplate.getForObject(ecommerceUrl + "/api/products?...", Map.class)  → ecommerce :8081
  → ApiResponse.success(결과)
```

**(b) 환불 — `POST /admin/refunds`**

```
web/AdminRefundController.refund(cmd)     (⚠ 인증 검사 없음 — AdminAuth 미주입)
  → client/ShopGateway.refund(paymentId, amount, reason)
      → req = HashMap{paymentId, amount, reason}                 (타입 DTO 아님 — raw Map)
      → RestTemplate.postForObject(paymentUrl + "/api/payments/refund", req, Map.class)  → payment :8082
  → ApiResponse.success(결과)
```

`preview-total` 은 외부 호출 없이 `util/AdminPriceCalculator.calcTotal(subtotal, discount)` 로
**소계+세금(10%)−할인**을 계산한다. 세금·합계 모두 `Math.floor`(버림) 처리.

## 설정 (`src/main/resources/application.yml`)

```yaml
server:
  port: 8083
admin:
  token: admin-secret              # ⚠ 하드코딩 기본값 (R5)
ecommerce:
  base-url: http://localhost:8081  # ⚠ 하드코딩 기본값 (R5)
payment:
  base-url: http://localhost:8082  # ⚠ 하드코딩 기본값 (R5)
```

값은 `@Value("${...:기본값}")` 로 주입한다(`AdminAuth`, `ShopGateway` 생성자). 운영 분리·토큰 보호를
위해 환경변수/프로파일 외부화가 필요하다 — [`../../docs/known-issues.md`](../../docs/known-issues.md) R5.

## 의존성

- **빌드 의존**: `core-framework`(`ApiResponse`, `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`),
  `common-util`, `spring-boot-starter-web`. JPA·DB 의존 없음(`admin/build.gradle`).
- **런타임 의존**: ecommerce `:8081`, payment `:8082` 가 떠 있어야 동작한다. 권장 기동 순서는
  **ecommerce → payment → admin** ([`../../docs/architecture.md`](../../docs/architecture.md) "실행 방법").
- **응답 봉투**: 모든 응답은 `ApiResponse<T> = { code, message, data }` (성공 시 `code="0000"`).
