# admin 알려진 문제 · 기술 부채

> 이 메모는 `admin` 모듈에 한정된 결함/안티패턴을 모은다. 전체 코드베이스의 목록과 분류 체계는
> 모노레포 [`../../docs/known-issues.md`](../../docs/known-issues.md) 에 있다.
>
> 분류: 🐞 정합성/보안 결함 / ⚠️ 위험·안티패턴. 영향도: 높음/중간/낮음.
> 수정 성격: **동작변경**(behavior-changing) / **동작보존**(behavior-preserving).
> 수정 전에는 **현재 동작을 고정하는 characterization 테스트**를 먼저 작성한다.

## admin 신규 발견 (모노레포 목록에 아직 없음)

| # | 위치 | 증상 | 영향 | 수정 성격 |
|---|------|------|------|----------|
| A1 | `web/AdminRefundController.java` | **`POST /admin/refunds` 무인증.** 다른 컨트롤러(`AdminProductController`, `AdminOrderController`)는 `adminAuth.check(token)` 를 호출하지만, 환불 컨트롤러는 `AdminAuth` 를 **주입조차 하지 않아** 토큰 검사가 전혀 없다. 인증 없이 환불을 트리거할 수 있다. | 높음 | 동작변경(인증 추가) |

> A1 은 환불(가장 민감한 작업)이 무인증이라는 점에서 우선순위가 높다. 모노레포
> `../../docs/known-issues.md` 에 정식 편입할 후보이며, 현재는 이 모듈 문서에만 기록한다.
> 수정 시 다른 컨트롤러와 동일하게 `X-Admin-Token` → `adminAuth.check(token)` 패턴을 적용한다.

## 모노레포 목록 중 admin 해당 항목 (참조)

코드 체계는 [`../../docs/known-issues.md`](../../docs/known-issues.md) 와 동일하다. 상세·배경은 링크 참조.

| # | 위치 | 내용 | 수정 성격 |
|---|------|------|----------|
| R2 | `client/ShopGateway.java` | 서비스 간 HTTP 를 타입 DTO 없이 raw `Map` 으로 주고받고 캐스팅. 계약이 코드에 드러나지 않음. [ADR-0005](../../docs/adr/0005-map-based-inter-service-http.md) | 동작보존 |
| R5 | `application.yml`, `@Value` 기본값 | 서비스 URL·`admin.token`(=`admin-secret`)·포트가 하드코딩. 운영 분리 불가, 토큰 노출. | 동작보존(외부화) |
| R6 | `util/AdminPriceCalculator.java` | ecommerce `PricingService` 계산식을 복붙. 로직 이중 관리(한쪽만 고치면 불일치). | 동작보존(통합) |
| R8 | `config/RestTemplateConfig.java` | `new RestTemplate()` 에 타임아웃 미설정. 다운스트림 지연 시 호출 스레드 무한 대기 위험. | 동작보존 |

## 연관 (모듈 외부지만 admin 동작에 영향)

- 환불 누계가 결제액을 초과해도 막지 않는 결함은 **payment 서비스**(`RefundService.refund`)에 있다
  (모노레포 known-issues **B6**). admin 의 `/admin/refunds` 는 이 경로를 호출하므로 함께 인지한다.
- 금액 `double`/버림 처리는 전사 공통 사안이다(모노레포 **B3**, [ADR-0003](../../docs/adr/0003-money-as-double.md)).
