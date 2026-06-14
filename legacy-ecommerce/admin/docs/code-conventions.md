# admin 코드 컨벤션

> 이 모듈은 모노레포 공통 컨벤션 [`../../docs/code-conventions.md`](../../docs/code-conventions.md) 을
> 그대로 따른다(패키지 구조, 생성자 주입, DTO=`record`, `ApiResponse`/`BusinessException` 에러 처리,
> 한국어 주석/영어 식별자, 빌드 규칙 등). 아래에는 **admin 에 한정된 보충·차이점만** 적는다.

## 구조상의 차이

- **게이트웨이 전용 모듈**이라 공통 컨벤션의 `service / domain / repository` 계층이 **없다**.
  비즈니스 로직·영속성은 다운스트림(ecommerce/payment)이 소유한다.
- admin 만의 `security` 패키지가 있다(`AdminAuth`).
- 컨트롤러는 "요청 받기 → (인증) → `ShopGateway` 위임 → `ApiResponse.success(...)` 래핑"으로 **얇게** 유지한다.

## 인증 패턴 (이 모듈의 핵심 규칙)

보호 엔드포인트는 헤더 토큰을 받아 컨트롤러 진입부에서 명시적으로 검증한다.

```java
@GetMapping
public ApiResponse<Object> list(
        @RequestHeader(value = "X-Admin-Token", required = false) String token,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    adminAuth.check(token);                       // 실패 시 BusinessException(ErrorCode.UNAUTHORIZED)
    return ApiResponse.success(gateway.listProducts(page, size));
}
```

- `AdminAuth` 는 생성자 주입(`private final AdminAuth adminAuth`)으로 받는다.
- **새 엔드포인트는 반드시 이 패턴을 따른다.** 인증 없이 통과되는 엔드포인트를 만들지 않는다.
- ⚠️ 현재 `AdminRefundController` 는 이 패턴을 어겨 `AdminAuth` 를 주입조차 하지 않는다
  (= `/admin/refunds` 무인증). 알려진 결함이며 따라 하지 말 것 — [`known-issues.md`](./known-issues.md).

## ⚠️ 모방 금지 안티패턴 (이 모듈에 존재)

손대는 김에 개선하되, **새 코드에서 그대로 답습하지 않는다.** 코드(R2/R5/R6/R8)는 모노레포
[`../../docs/known-issues.md`](../../docs/known-issues.md) 체계를 그대로 쓴다.

- **R2 — raw `Map` HTTP 통신**: `ShopGateway` 가 요청/응답을 타입 DTO 없이 `Map` 으로 주고받고
  캐스팅한다. 계약이 코드에 드러나지 않는다. 배경 [ADR-0005](../../docs/adr/0005-map-based-inter-service-http.md).
- **R6 — 로직 복붙**: `AdminPriceCalculator` 가 ecommerce `PricingService` 의 계산식을 복사했다.
  새 계산 로직은 한 곳(`MoneyUtils`/`PricingService`)에 모은다.
- **R8 — 타임아웃 없음**: `RestTemplateConfig` 가 `new RestTemplate()` 만 만든다. 다운스트림 지연 시
  호출 스레드가 무한 대기할 수 있다.
- **R5 — 하드코딩 설정**: 서비스 URL·`admin.token`(`admin-secret`)이 `@Value` 기본값으로 박혀 있다.
  운영은 환경변수/프로파일로 외부화한다.

## 금액 처리

- 금액은 `double` 로 다룬다(공통 컨벤션, [ADR-0003](../../docs/adr/0003-money-as-double.md)).
- `AdminPriceCalculator` 는 `Math.floor` 로 **버림** 처리한다(공통 `MoneyUtils.round` 와 같은 동작).
  새 계산이 필요하면 복제하지 말고 `MoneyUtils` 를 재사용한다.

## 주석 / 언어

- 본문 주석·도메인 용어는 한국어, 식별자/타입은 영어(공통 규칙과 동일).
- 클래스 상단에 한 줄 `/** ... */` 로 역할을 적는 기존 스타일을 유지한다(`AdminAuth`, `ShopGateway` 참고).
  과도한 주석은 더하지 않는다.
